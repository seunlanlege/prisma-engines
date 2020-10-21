package writes.nestedMutations.alreadyConverted

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class NestedUpsertMutationInsideUpdateSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBaseV11 {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)
  lazy val isMySQL                    = connectorTag == ConnectorTag.MySqlConnectorTag

  "a PM to C1!  relation with a child already in a relation" should "work with create" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: {c: "c1", c_1: "foo", c_2: "bar"}
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
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parentIdentifier
         |    data:{
         |      childrenOpt: {
         |        upsert: {
         |          where: {c: "DOES NOT EXIST"}
         |          update: {c: { set: "DOES NOT MATTER" }}
         |          create :{c: "c2", c_1: "asdf", c_2: "lol"}
         |        }
         |      }
         |   }
         |){
         |  childrenOpt {
         |    c
         |  }
         |}
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

    }
  }

  "a PM to C1!  relation with a child already in a relation" should "work with update" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: {c: "c1", c_1: "foo", c_2: "bar"}
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
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parentIdentifier
         |    data:{
         |    childrenOpt: {upsert: {
         |    where: {c: "c1"}
         |    update: {c: { set: "updated C" }}
         |    create :{c: "DOES NOT MATTER", c_1: "foo", c_2: "bar"}
         |    }}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"updated C"}]}}}""")

    }
  }

  "a PM to C1  relation with the parent already in a relation" should "work through a nested mutation by unique for create" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentOpt).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server
        .query(
          s"""mutation {
          |  createParent(data: {
          |    p: "p1", p_1: "p", p_2: "1"
          |    childrenOpt: {
          |      create: [{c: "c1", c_1: "foo", c_2: "bar"}, {c: "c2", c_1: "juuh", c_2: "lol"}]
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
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {upsert: [{
         |    where: {c: "DOES NOT EXIST"}
         |    update: {c: {set: "DOES NOT MATTER"}}
         |    create :{c: "new C", c_1: "omg", c_2: "lolz"}
         |    }]}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"new C"}]}}}""")

    }
  }

  "a PM to C1  relation with the parent already in a relation" should "work through a nested mutation by unique for update" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentOpt).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server
        .query(
          s"""mutation {
          |  createParent(data: {
          |    p: "p1", p_1: "p", p_2: "1"
          |    childrenOpt: {
          |      create: [{c: "c1", c_1: "a", c_2: "b"}, {c: "c2", c_1: "a2", c_2: "b2"}]
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
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {upsert: [{
         |    where: {c: "c1"}
         |    update: {c: {set:"updated C"}}
         |    create :{c: "DOES NOT MATTER", c_1: "DOES NOT MATTER", c_2: "DOES NOT MATTER"}
         |    }]}
         |  }){
         |    childrenOpt (orderBy: { c: asc }){
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c2"},{"c":"updated C"}]}}}""")

    }
  }

  "a PM to CM  relation with the children already in a relation" should "work through a nested mutation by unique for update" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentList).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: [{c: "c1", c_1: "foo", c_2: "bar"},{c: "c2", c_1: "buu", c_2: "quu"}]
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
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parentIdentifier
         |    data:{
         |      childrenOpt: {
         |        upsert: [{
         |          where:  {c: "c2"}
         |          update: {c: {set: "updated C"}}
         |          create: {c: "DOES NOT MATTER", c_1: "foo", c_2: "bar"}
         |        }]
         |      }
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"updated C"}]}}}""")

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]},{"c":"updated C","parentsOpt":[{"p":"p1"}]}]}}""")

    }
  }

  "a PM to CM  relation with the children already in a relation" should "work through a nested mutation by unique for create" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentList).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: [{c: "c1", c_1: "foo", c_2: "bar"},{c: "c2", c_1: "puu", c_2: "quu"}]
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
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {upsert: [{
         |      where: {c: "DOES NOT EXIST"}
         |      update: {c: {set: "DOES NOT MATTER"}}
         |      create :{c: "updated C", c_1: "lolz", c_2: "miau"}
         |    }]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"updated C"}]}}}""")

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]},{"c":"c2","parentsOpt":[{"p":"p1"}]},{"c":"updated C","parentsOpt":[{"p":"p1"}]}]}}""")

    }
  }

  "a one to many relation" should "be upsertable by id through a nested mutation" ignore {
    val schema = s"""model Comment{
                            id   String  @id @default(cuid())
                            text String?
                            todo Todo?
                        }

                        model Todo{
                            id       String    @id @default(cuid())
                            comments Comment[] $relationInlineAttribute
                        }"""

    val project = SchemaDsl.fromStringV11() { schema }
    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1"}, {text: "comment2"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments (orderBy: { id: asc }){ id }
        |  }
        |}""",
      project
    )

    val todoId     = createResult.pathAsString("data.createTodo.id")
    val comment1Id = createResult.pathAsString("data.createTodo.comments.[0].id")

    val result = server.query(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        upsert: [
         |          {where: {id: "$comment1Id"}, update: {text: {set: "update comment1"}}, create: {text: "irrelevant"}},
         |          {where: {id: "5beea4aa6183dd734b2dbd9b"}, update: {text: {set: "irrelevant"}}, create: {text: "new comment3"}},
         |        ]
         |      }
         |    }
         |  ){
         |    comments (orderBy: { id: asc }){
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsString("data.updateTodo.comments.[0].text").toString, """update comment1""")
    mustBeEqual(result.pathAsString("data.updateTodo.comments.[1].text").toString, """comment2""")
    mustBeEqual(result.pathAsString("data.updateTodo.comments.[2].text").toString, """new comment3""")
  }

  "a one to many relation" should "only update nodes that are connected" ignore {
    val schema = s"""model Comment{
                            id   String  @id @default(cuid())
                            text String?
                            todo Todo?
                        }

                        model Todo{
                            id       String    @id @default(cuid())
                            comments Comment[] $relationInlineAttribute
                        }"""

    val project = SchemaDsl.fromStringV11() { schema }
    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""",
      project
    )
    val todoId     = createResult.pathAsString("data.createTodo.id")
    val comment1Id = createResult.pathAsString("data.createTodo.comments.[0].id")

    val commentResult = server.query(
      """mutation {
        |  createComment(
        |    data: {
        |      text: "comment2"
        |    }
        |  ){
        |    id
        |  }
        |}""",
      project
    )
    val comment2Id = commentResult.pathAsString("data.createComment.id")

    val result = server.query(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        upsert: [
         |          {where: {id: "$comment1Id"}, update: {text: {set: "update comment1"}}, create: {text: "irrelevant"}},
         |          {where: {id: "$comment2Id"}, update: {text: {set: "irrelevant"}}, create: {text: "new comment3"}},
         |        ]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsString("data.updateTodo.comments.[0].text").toString, """update comment1""")
    mustBeEqual(result.pathAsString("data.updateTodo.comments.[1].text").toString, """new comment3""")
  }

  "a one to many relation" should "generate helpful error messages" ignore {
    val schema = s"""model Comment{
                            id            String  @id @default(cuid())
                            text          String?
                            uniqueComment String  @unique
                            todo          Todo?
                        }

                        model Todo{
                            id         String    @id @default(cuid())
                            uniqueTodo String    @unique
                            comments   Comment[] $relationInlineAttribute
                        }"""

    val project = SchemaDsl.fromStringV11() { schema }
    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createTodo(
        |    data: {
        |      uniqueTodo: "todo"
        |      comments: {
        |        create: [{text: "comment1", uniqueComment: "comments"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""",
      project
    )
    val todoId     = createResult.pathAsString("data.createTodo.id")
    val comment1Id = createResult.pathAsString("data.createTodo.comments.[0].id")

    val errorTarget = () match {
      case _ if isMySQL => "constraint: `uniqueComment`"
      case _            => "fields: (`uniqueComment`)"
    }

    server.queryThatMustFail(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        upsert: [
         |          {where: {id: "5beea4aa6183dd734b2dbd9b"}, update: {text: {set: "update comment1"}}, create: {text: "irrelevant", uniqueComment: "comments"}},
         |        ]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 2002,
      errorContains = s"Unique constraint failed on the $errorTarget"
    )
  }

  "a deeply nested mutation" should "execute all levels of the mutation" ignore {

    val schema = s"""     model List{
                            id    String @id @default(cuid())
                            name  String
                            todos Todo[] $relationInlineAttribute
                        }

                        model Todo{
                            id    String @id @default(cuid())
                            title String
                            list  List?
                            tags  Tag[] $relationInlineAttribute
                        }

                        model Tag{
                            id   String @id @default(cuid())
                            name String
                            todo Todo
                        }"""

    val project = SchemaDsl.fromStringV11() { schema }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createList(data: {
        |    name: "the list",
        |    todos: {
        |      create: [
        |        {
        |          title: "the todo"
        |          tags: {
        |            create: [
        |              {name: "the tag"}
        |            ]
        |          }
        |        }
        |      ]
        |    }
        |  }) {
        |    id
        |    todos {
        |      id
        |      tags {
        |        id
        |      }
        |    }
        |  }
        |}
      """

    val createResult = server.query(createMutation, project)
    val listId       = createResult.pathAsString("data.createList.id")
    val todoId       = createResult.pathAsString("data.createList.todos.[0].id")
    val tagId        = createResult.pathAsString("data.createList.todos.[0].tags.[0].id")

    val updateMutation =
      s"""
         |mutation  {
         |  updateList(
         |    where: {
         |      id: "$listId"
         |    }
         |    data: {
         |      todos: {
         |        upsert: [
         |          {
         |            where: { id: "$todoId" }
         |            create: { title: "irrelevant" }
         |            update: {
         |              tags: {
         |                upsert: [
         |                  {
         |                    where: { id: "$tagId" }
         |                    update: { name: { set: "updated tag" }}
         |                    create: { name: "irrelevant" }
         |                  },
         |                  {
         |                    where: { id: "5beea4aa6183dd734b2dbd9b" }
         |                    update: { name: { set: "irrelevant" }}
         |                    create: { name: "new tag" }
         |                  },
         |                ]
         |              }
         |            }
         |          }
         |        ]
         |      }
         |    }
         |  ) {
         |    name
         |    todos {
         |      title
         |      tags {
         |        name
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)
    result.pathAsString("data.updateList.todos.[0].tags.[0].name") should equal("updated tag")
    result.pathAsString("data.updateList.todos.[0].tags.[1].name") should equal("new tag")
  }

  "a deeply nested mutation with upsert" should "work on miss on id" ignore {
    val schema = s"""     model List{
                            id    String @id @default(cuid())
                            name  String
                            todos Todo[] $relationInlineAttribute
                        }

                        model Todo{
                            id    String @id @default(cuid())
                            title String
                            list  List?
                            tags  Tag[] $relationInlineAttribute
                        }

                        model Tag{
                            id   String @id @default(cuid())
                            name String
                            todo Todo?
                        }"""

    val project = SchemaDsl.fromStringV11() { schema }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createList(data: {
        |    name: "the list",
        |    todos: {
        |      create: [
        |        {
        |          title: "the todo"
        |          tags: {
        |            create: [
        |              {name: "the tag"}
        |            ]
        |          }
        |        }
        |      ]
        |    }
        |  }) {
        |    id
        |    todos {
        |      id
        |      tags {
        |        id
        |      }
        |    }
        |  }
        |}
      """

    val createResult = server.query(createMutation, project)
    val listId       = createResult.pathAsString("data.createList.id")
    val todoId       = createResult.pathAsString("data.createList.todos.[0].id")
    val tagId        = createResult.pathAsString("data.createList.todos.[0].tags.[0].id")

    val updateMutation =
      s"""
         |mutation  {
         |  updateList(
         |    where: {
         |      id: "$listId"
         |    }
         |    data: {
         |      todos: {
         |        upsert: [
         |          {
         |            where: { id: "5beea4aa6183dd734b2dbd9b" }
         |            create: { title: "new todo" tags: { create: [ {name: "the tag"}]}}
         |            update: { title: { set: "updated todo" }}
         |          }
         |        ]
         |      }
         |    }
         |  ) {
         |    name
         |    todos {
         |      title
         |      tags {
         |        name
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)
    result.pathAsString("data.updateList.todos.[0].tags.[0].name") should equal("the tag")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path for update case" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String @id @default(cuid())
                                             |  nameMiddle String @unique
                                             |  tops       Top[]
                                             |  bottoms    Bottom[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String   @id @default(cuid())
                                             |  nameBottom String   @unique
                                             |  middles    Middle[]
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {upsert: [{ where: {nameBottom: "the bottom"},
         |                                           create:  {nameBottom: "Should not matter"}
         |                                           update:  {nameBottom: "updated bottom"}
         |                      }]
         |              }
         |              }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: { id: asc }){
         |      nameMiddle
         |      bottoms (orderBy: { id: asc }){
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"updated bottom"},{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")

    server.query("query{bottoms(orderBy: { id: asc }){nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"updated bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path for create case" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String @id @default(cuid())
                                             |  nameMiddle String @unique
                                             |  tops       Top[]
                                             |  bottoms    Bottom[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String   @id @default(cuid())
                                             |  nameBottom String   @unique
                                             |  middles    Middle[]
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {upsert: [{ where: {nameBottom: "does not exist"},
         |                                           create:  {nameBottom: "created bottom"}
         |                                           update:  {nameBottom: "should not matter"}
         |                      }]
         |              }
         |              }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: { id: asc }) {
         |      nameMiddle
         |      bottoms (orderBy: { id: asc }){
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"created bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")

    server.query("query{bottoms(orderBy: { id: asc }){nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"the bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"},{"nameBottom":"created bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path for update case with no backrelations" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String   @id @default(cuid())
                                             |  nameMiddle String   @unique
                                             |  bottoms    Bottom[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String @id @default(cuid())
                                             |  nameBottom String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {upsert: [{ where: {nameBottom: "the bottom"},
         |                                           create:  {nameBottom: "Should not matter"}
         |                                           update:  {nameBottom: "updated bottom"}
         |                      }]
         |              }
         |              }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: { id: asc }){
         |      nameMiddle
         |      bottoms (orderBy: { id: asc }){
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"updated bottom"},{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")

    server.query("query{bottoms(orderBy: { id: asc }){nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"updated bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path for create case with no backrelations" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Middle {
                                             |  id          String   @id @default(cuid())
                                             |  nameMiddle  String   @unique
                                             |  bottoms     Bottom[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String @id @default(cuid())
                                             |  nameBottom String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {upsert: [{ where: {nameBottom: "does not exist"},
         |                                           create:  {nameBottom: "created bottom"}
         |                                           update:  {nameBottom: "should not matter"}
         |                      }]
         |              }
         |              }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: { id: asc }){
         |      nameMiddle
         |      bottoms (orderBy: { id: asc }){
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"created bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")

    server.query("query{bottoms(orderBy: { id: asc }){nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"the bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"},{"nameBottom":"created bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path for update" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String @id @default(cuid())
                                             |  nameMiddle String @unique
                                             |  tops       Top[]
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String @id @default(cuid())
                                             |  nameBottom String @unique
                                             |  middle     Middle
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {create: { nameBottom: "the bottom"}}
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottom: {create: { nameBottom: "the second bottom"}}
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottom: {upsert: {create: {nameBottom: "should not matter"},
         |                                        update: {nameBottom: "updated bottom"}}}
         |              }
         |              }]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: { id: asc }){
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom"}},{"nameMiddle":"the second middle","bottom":{"nameBottom":"the second bottom"}}]}}}""")

    server.query("query{bottoms(orderBy: { id: asc }){nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"updated bottom"},{"nameBottom":"the second bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path for create" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  tops       Top[]
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String @id @default(cuid())
                                             |  nameBottom String @unique
                                             |  middle     Middle?
                                             |}""" }

    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottom: {create: { nameBottom: "the second bottom"}}
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottom: {upsert: {create: {nameBottom: "created bottom"},
         |                                        update: {nameBottom: "should not matter"}}}
         |              }
         |              }]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: { id: asc }) {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottom":{"nameBottom":"created bottom"}},{"nameMiddle":"the second middle","bottom":{"nameBottom":"the second bottom"}}]}}}""".parseJson)

    server.query("query{bottoms(orderBy: { id: asc }){nameBottom}}", project) should be(
      """{"data":{"bottoms":[{"nameBottom":"the second bottom"},{"nameBottom":"created bottom"}]}}""".parseJson)
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path  and back relations are missing and node edges follow model edges for update" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String @id @default(cuid())
                                             |  nameTop String @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String @id @default(cuid())
                                             |  nameMiddle String @unique
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  nameBottom String  @unique
                                             |  below      Below[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Below {
                                             |  id        String @id @default(cuid())
                                             |  nameBelow String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation a {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: { nameBottom: "the bottom"
        |            below: {
        |            create: [{ nameBelow: "below"}, { nameBelow: "second below"}]}
        |        }}}
        |        }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |               nameMiddle: "updated middle"
         |               bottom: {
         |                update: {
         |                  nameBottom: "updated bottom"
         |                  below: { upsert: {
         |                    where: {nameBelow: "below"}
         |                    create:{nameBelow: "should not matter"}
         |                    update:{nameBelow: "updated below"}
         |                  }
         |          }
         |                }
         |          }
         |       }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |        below (orderBy: { id: asc }){
         |           nameBelow
         |        }
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom","below":[{"nameBelow":"updated below"},{"nameBelow":"second below"}]}}}}}""")

    server.query("query{belows(orderBy: { id: asc }){nameBelow}}", project).toString should be(
      """{"data":{"belows":[{"nameBelow":"updated below"},{"nameBelow":"second below"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path  and back relations are missing and node edges follow model edges for create" ignore {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  nameBottom String  @unique
                                             |  below      Below[] $relationInlineAttribute
                                             |}
                                             |
                                             |model Below {
                                             |  id        String @id @default(cuid())
                                             |  nameBelow String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation a {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: { nameBottom: "the bottom"
        |            below: {
        |            create: [{ nameBelow: "below"}, { nameBelow: "second below"}]}
        |        }}}
        |        }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |               nameMiddle: "updated middle"
         |               bottom: {
         |                update: {
         |                  nameBottom: "updated bottom"
         |                  below: { upsert: {
         |                    where: {nameBelow: "Does not exist"}
         |                    create:{nameBelow: "created below"}
         |                    update:{nameBelow: "should not matter"}
         |                  }
         |          }
         |                }
         |          }
         |       }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |        below{
         |           nameBelow
         |        }
         |
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom","below":[{"nameBelow":"below"},{"nameBelow":"second below"},{"nameBelow":"created below"}]}}}}}""")

    server.query("query{belows(orderBy: { id: asc }){nameBelow}}", project).toString should be(
      """{"data":{"belows":[{"nameBelow":"below"},{"nameBelow":"second below"},{"nameBelow":"created below"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path for update" ignore {
    val project = SchemaDsl.fromStringV11() { """model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  top        Top?
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  middle     Middle?
                                             |  nameBottom String  @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: {
        |              nameBottom: "the bottom"
        |            }
        |          }
        |        }
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {upsert: {create: {nameBottom: "should not matter"},
         |                                update: {nameBottom: "updated bottom"}
         |                       }
         |              }
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom"}}}}}""")

    server.query("query{bottoms{nameBottom}}", project).toString should be("""{"data":{"bottoms":[{"nameBottom":"updated bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path for create" ignore {
    val project = SchemaDsl.fromStringV11() { """model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String @id @default(cuid())
                                             |  nameMiddle String @unique
                                             |  top        Top?
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  middle     Middle?
                                             |  nameBottom String  @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:{nameMiddle: "the middle"}
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {upsert: {create: {nameBottom: "created bottom"},
         |                                update: {nameBottom: "should not matter"}
         |                       }
         |              }
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"created bottom"}}}}}""")

    server.query("query{bottoms{nameBottom}}", project).toString should be("""{"data":{"bottoms":[{"nameBottom":"created bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path for update when there are no backrelations" ignore {
    val project = SchemaDsl.fromStringV11() { """model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String @id @default(cuid())
                                             |  nameBottom String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: {
        |              nameBottom: "the bottom"
        |            }
        |          }
        |        }
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {upsert: {create: {nameBottom: "should not matter"},
         |                                update: {nameBottom: "updated bottom"}
         |                       }
         |              }
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom"}}}}}""")

    server.query("query{bottoms{nameBottom}}", project).toString should be("""{"data":{"bottoms":[{"nameBottom":"updated bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path for create when there are no backrelations" ignore {
    val project = SchemaDsl.fromStringV11() { """model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  nameBottom String  @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:{nameMiddle: "the middle"}
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {upsert: {create: {nameBottom: "created bottom"},
         |                                update: {nameBottom: "should not matter"}
         |                       }
         |              }
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"created bottom"}}}}}""")

    server.query("query{bottoms{nameBottom}}", project).toString should be("""{"data":{"bottoms":[{"nameBottom":"created bottom"}]}}""")
  }

  "a nested upsert for a model with an id field of model uuid" should "work" ignore {
    val project = SchemaDsl.fromStringV11() {
      s"""
         |model List {
         |  id    String @id @default(cuid())
         |  todos Todo[] $relationInlineAttribute
         |}
         |
         |model Todo {
         |  id    String @id @default(uuid())
         |  title String
         |}
       """
    }
    database.setup(project)

    val listId = server
      .query("""
        |mutation {
        |  createList(data: {}) {
        |    id
        |  }
        |}
      """,
             project)
      .pathAsString("data.createList.id")

    val result = server.query(
      s"""
        |mutation {
        |  updateList(
        |  where: {id: "$listId"}
        |  data: {
        |    todos: {
        |      upsert: [
        |        {
        |          where: { id: "00000000-0000-0000-0000-000000000000" }
        |          create: { title: "the todo" }
        |          update: { title: "the updated title" }
        |        }
        |      ]
        |    }
        |  }){
        |    todos {
        |      id
        |      title
        |    }
        |  }
        |}
      """,
      project
    )

    result.pathAsString("data.updateList.todos.[0].title") should equal("the todo")
    val theUuid = result.pathAsString("data.updateList.todos.[0].id")
    UUID.fromString(theUuid) // should now blow up
  }
}
