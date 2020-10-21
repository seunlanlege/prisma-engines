package writes.nestedMutations.alreadyConverted

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class NestedUpdateManyMutationInsideUpdateSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBaseV11 {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  "A 1-n relation" should "error if trying to use nestedUpdateMany" in {
    schemaWithRelation(onParent = ChildOpt, onChild = ParentOpt).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parent1Result = server
        .query(
          s"""mutation {
          |  createParent(data: {p: "p1", p_1: "p", p_2: "1"})
          |  {
          |    ${t.parent.selection}
          |  }
          |}""",
          project
        )

      val parent1Id = t.parent.where(parent1Result, "data.createParent")

      val res = server.queryThatMustFail(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parent1Id
         |  data:{
         |    p: { set: "p2" }
         |    childOpt: {updateMany: {
         |        where:{c: "c"}
         |        data: {c: { set: "newC" }}
         |
         |    }}
         |  }){
         |    ${t.parent.selection}
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project,
        errorCode = 2009,
        errorContains =
          """`Field does not exist on enclosing type.` at `Mutation.updateParent.data.ParentUpdateInput.childOpt.ChildUpdateOneWithoutParentOptInput.updateMany`"""
      )
    }
  }

  "a PM to C1!  relation " should "work" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val (parent1Id, parent2Id) = setupData(project, t)

      server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parent1Id
         |    data:{
         |    childrenOpt: {updateMany: {
         |        where: { c: { contains:"c"} }
         |        data: { non_unique: { set: "updated" }}
         |    }}
         |  }){
         |    childrenOpt {
         |      c
         |      non_unique
         |    }
         |  }
         |}
      """,
        project
      )

      server.query("query{parents{p,childrenOpt{c, non_unique}}}", project).toString() should be(
        """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","non_unique":"updated"},{"c":"c2","non_unique":"updated"}]},{"p":"p2","childrenOpt":[{"c":"c3","non_unique":null},{"c":"c4","non_unique":null}]}]}}""")
    }
  }

  "a PM to C1  relation " should "work" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentOpt).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val (parent1Id, parent2Id) = setupData(project, t)

      server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parent1Id
         |    data:{
         |    childrenOpt: {updateMany: {
         |        where: { c: { contains:"c" } }
         |        data: { non_unique: { set: "updated" }}
         |    }}
         |  }){
         |    childrenOpt {
         |      c
         |      non_unique
         |    }
         |  }
         |}
      """,
        project
      )

      server.query("query{parents{p,childrenOpt{c, non_unique}}}", project).toString() should be(
        """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","non_unique":"updated"},{"c":"c2","non_unique":"updated"}]},{"p":"p2","childrenOpt":[{"c":"c3","non_unique":null},{"c":"c4","non_unique":null}]}]}}""")
    }
  }

  "a PM to CM  relation " should "work" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentList).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val (parent1Id, parent2Id) = setupData(project, t)

      server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parent1Id
         |    data:{
         |    childrenOpt: {updateMany: {
         |        where: {c: { contains:"c" } }
         |        data: {non_unique: { set: "updated" }}
         |    }}
         |  }){
         |    childrenOpt {
         |      c
         |      non_unique
         |    }
         |  }
         |}
      """,
        project
      )

      server.query("query{parents{p,childrenOpt{c, non_unique}}}", project).toString() should be(
        """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","non_unique":"updated"},{"c":"c2","non_unique":"updated"}]},{"p":"p2","childrenOpt":[{"c":"c3","non_unique":null},{"c":"c4","non_unique":null}]}]}}""")
    }
  }

  "a PM to C1!  relation " should "work with several updateManys" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val (parent1Id, parent2Id) = setupData(project, t)

      server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parent1Id
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: {c: { contains:"1" } }
         |        data: {non_unique: { set: "updated1" }}
         |    },
         |    {
         |        where: {c: { contains:"2" } }
         |        data: {non_unique: { set: "updated2" }}
         |    }
         |    ]}
         |  }){
         |    childrenOpt {
         |      c
         |      non_unique
         |    }
         |  }
         |}
      """,
        project
      )

      server.query("query{parents{p,childrenOpt{c, non_unique}}}", project).toString() should be(
        """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","non_unique":"updated1"},{"c":"c2","non_unique":"updated2"}]},{"p":"p2","childrenOpt":[{"c":"c3","non_unique":null},{"c":"c4","non_unique":null}]}]}}""")
    }
  }

  "a PM to C1!  relation " should "work with empty Filter" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val (parent1Id, parent2Id) = setupData(project, t)

      server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parent1Id
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: {}
         |        data: { non_unique: { set: "updated1" }}
         |    }
         |    ]}
         |  }){
         |    childrenOpt {
         |      c
         |      non_unique
         |    }
         |  }
         |}
      """,
        project
      )

      server.query("query{parents{p,childrenOpt{c, non_unique}}}", project).toString() should be(
        """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","non_unique":"updated1"},{"c":"c2","non_unique":"updated1"}]},{"p":"p2","childrenOpt":[{"c":"c3","non_unique":null},{"c":"c4","non_unique":null}]}]}}""")
    }
  }

  "a PM to C1!  relation " should "not change anything when there is no hit" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val (parent1Id, parent2Id) = setupData(project, t)

      server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parent1Id
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: { c: { contains:"3" }}
         |        data: { non_unique: { set: "updated3" }}
         |    },
         |    {
         |        where: { c: { contains:"4" }}
         |        data: { non_unique: { set: "updated4" }}
         |    }
         |    ]}
         |  }){
         |    childrenOpt {
         |      c
         |      non_unique
         |    }
         |  }
         |}
      """,
        project
      )

      server.query("query{parents{p,childrenOpt{c, non_unique}}}", project).toString() should be(
        """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","non_unique":null},{"c":"c2","non_unique":null}]},{"p":"p2","childrenOpt":[{"c":"c3","non_unique":null},{"c":"c4","non_unique":null}]}]}}""")
    }
  }

  //optional ordering

  "a PM to C1!  relation " should "work when multiple filters hit" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val (parent1Id, parent2Id) = setupData(project, t)

      server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parent1Id
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: { c: { contains: "c" }}
         |        data: { non_unique: { set: "updated1" }}
         |    },
         |    {
         |        where: { c: { contains: "c1" }}
         |        data: { non_unique: { set: "updated2" }}
         |    }
         |    ]}
         |  }){
         |    childrenOpt (orderBy: { c: asc }){
         |      c
         |      non_unique
         |    }
         |  }
         |}
      """,
        project
      )

      server.query("query{parents{p,childrenOpt(orderBy: { c: asc }){c, non_unique}}}", project).toString() should be(
        """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","non_unique":"updated2"},{"c":"c2","non_unique":"updated1"}]},{"p":"p2","childrenOpt":[{"c":"c3","non_unique":null},{"c":"c4","non_unique":null}]}]}}""")
    }
  }

  private def setupData(project: Project, t: TestAbstraction) = {
    val parent1Result = server.query(
      s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: [{c: "c1", c_1: "foo", c_2: "bar"},{c: "c2", c_1: "fo", c_2: "lo"}]
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""",
      project
    )
    val parent1Id = t.parent.where(parent1Result, "data.createParent")

    val parent2Result = server.query(
      s"""mutation {
        |  createParent(data: {
        |    p: "p2", p_1: "p", p_2: "2"
        |    childrenOpt: {
        |      create: [{c: "c3", c_1: "ao", c_2: "bo"},{c: "c4", c_1: "go", c_2: "zo"}]
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""",
      project
    )
    val parent2Id = t.parent.where(parent2Result, "data.createParent")

    (parent1Id, parent2Id)
  }
}
