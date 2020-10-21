package writes.topLevelMutations

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class NonEmbeddedUpsertDesignSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities: Set[ConnectorCapability] = Set(JoinRelationLinksCapability)
  //region top level upserts

  val dmP1ToC1 = """model List{
        |   id     String  @id @default(cuid())
        |   uList  String? @unique
        |   todoId String?
        |
        |   todo  Todo?   @relation(fields: [todoId], references: [id])
        |}
        |
        |model Todo{
        |   id    String  @id @default(cuid())
        |   uTodo String? @unique
        |   list  List?
        |}"""

  val dmPMToCm = {
    val dm1 = """model List{
                   id String @id @default(cuid())
                   uList String? @unique
                   todoes Todo[] @relation(references: [id])
                }

                model Todo{
                   id String @id @default(cuid())
                   uTodo String? @unique
                   lists List[]
                   tags Tag[] @relation(references: [id])
                }

                model Tag{
                   id String @id @default(cuid())
                   uTag String @unique
                   todoes Todo[]
                }"""

    val dm2 = """model List{
                   id String @id @default(cuid())
                   uList String? @unique
                   todoes Todo[]
                }

                model Todo{
                   id String @id @default(cuid())
                   uTodo String? @unique
                   lists List[] @relation(references: [id])
                   tags Tag[]
                }

                model Tag{
                   id String @id @default(cuid())
                   uTag String @unique
                   todoes Todo[] @relation(references: [id])
                }"""

    val dm3 = """model List{
                   id String @id @default(cuid())
                   uList String? @unique
                   todoes Todo[]
                }

                model Todo{
                   id String @id @default(cuid())
                   uTodo String? @unique
                   lists List[]
                   tags Tag[]
                }

                model Tag{
                   id String @id @default(cuid())
                   uTag String @unique
                   todoes Todo[]
                }"""

    TestDataModels(mongo = Vector(dm1, dm2), sql = Vector(dm3))
  }

  "An upsert on the top level" should "execute a nested connect in the create branch" in {
    val project = SchemaDsl.fromStringV11()(dmP1ToC1)
    database.setup(project)

    server.query("""mutation{createTodo(data:{uTodo: "B"}){uTodo}}""", project)

    server
      .query(
        """mutation {upsertList(
           |                     where:{uList: "Does not Exist"}
           |                     create:{uList:"A" todo: {connect: {uTodo: "B"}}}
           |                     update:{todo: {connect: {uTodo: "Should not matter"}}}
           |){id}}""",
        project
      )

    val result = server.query(s"""query{lists {uList, todo {uTodo}}}""", project)
    result.toString should equal("""{"data":{"lists":[{"uList":"A","todo":{"uTodo":"B"}}]}}""")

    server.query(s"""query{todoes {uTodo}}""", project).toString should be("""{"data":{"todoes":[{"uTodo":"B"}]}}""")

    countItems(project, "lists") should be(1)
    countItems(project, "todoes") should be(1)
  }

  "An upsert on the top level" should "execute a nested connect in the update branch" in {
    val project = SchemaDsl.fromStringV11()(dmP1ToC1)
    database.setup(project)

    server.query("""mutation{createTodo(data:{uTodo: "B"}){uTodo}}""", project)
    server.query("""mutation{createList(data:{uList:"A"}){uList}}""", project)
    server
      .query(
        """mutation {upsertList(
          |                     where:{uList: "A"}
          |                     create:{uList:"A" todo: {connect: {uTodo: "Should not Matter"}}}
          |                     update:{todo: {connect: {uTodo: "B"}}}
          |){id}}""",
        project
      )

    val result = server.query(s"""query{lists {uList, todo {uTodo}}}""", project)
    result.toString should equal("""{"data":{"lists":[{"uList":"A","todo":{"uTodo":"B"}}]}}""")

    server.query(s"""query{todoes {uTodo}}""", project).toString should be("""{"data":{"todoes":[{"uTodo":"B"}]}}""")

    countItems(project, "lists") should be(1)
    countItems(project, "todoes") should be(1)
  }

  "An upsert on the top level" should "execute a nested disconnect in the update branch" in {
    val project = SchemaDsl.fromStringV11()(dmP1ToC1)
    database.setup(project)

    server.query("""mutation{createTodo(data:{uTodo: "B", list: {create: {uList:"A"}}}){uTodo}}""", project)

    server
      .query(
        """mutation {upsertList(
          |                     where:{uList: "A"}
          |                     create:{uList:"A" todo: {connect: {uTodo: "Should not Matter"}}}
          |                     update:{todo: {disconnect: true}}
          |){id}}""",
        project
      )

    val result = server.query(s"""query{lists {uList, todo {uTodo}}}""", project)
    result.toString should equal("""{"data":{"lists":[{"uList":"A","todo":null}]}}""")

    server.query(s"""query{todoes {uTodo}}""", project).toString should be("""{"data":{"todoes":[{"uTodo":"B"}]}}""")

    countItems(project, "lists") should be(1)
    countItems(project, "todoes") should be(1)
  }

  "An upsert on the top level" should "execute a nested delete in the update branch" in {
    val project = SchemaDsl.fromStringV11()(dmP1ToC1)
    database.setup(project)

    server.query("""mutation{createTodo(data:{uTodo: "B", list: {create: {uList:"A"}}}){uTodo}}""", project)

    server
      .query(
        """mutation {upsertList(
          |                     where:{uList: "A"}
          |                     create:{uList:"A" todo: {connect: {uTodo: "Should not Matter"}}}
          |                     update:{todo: {delete: true}}
          |){id}}""",
        project
      )

    val result = server.query(s"""query{lists {uList, todo {uTodo}}}""", project)
    result.toString should equal("""{"data":{"lists":[{"uList":"A","todo":null}]}}""")

    server.query(s"""query{todoes {uTodo}}""", project).toString should be("""{"data":{"todoes":[]}}""")

    countItems(project, "lists") should be(1)
    countItems(project, "todoes") should be(0)
  }

  "An upsert on the top level" should "only execute the nested create mutations of the correct update branch" ignore {
    val project = SchemaDsl.fromStringV11()(dmP1ToC1)
    database.setup(project)

    server.query("""mutation {createList(data: {uList: "A"}){id}}""", project)

    server
      .query(
        s"""mutation upsertListValues {upsertList(
           |                             where:{uList: "A"}
           |                             create:{uList:"B"  todo: {create: {uTodo: "B"}}}
           |                             update:{uList:"C"  todo: {create: {uTodo: "C"}}}
           |){id}}""".stripMargin,
        project
      )

    val result = server.query(s"""query{lists {uList, todo {uTodo}}}""", project)
    result.toString should equal("""{"data":{"lists":[{"uList":"C","todo":{"uTodo":"C"}}]}}""")

    server.query(s"""query{todoes {uTodo}}""", project).toString should be("""{"data":{"todoes":[{"uTodo":"C"}]}}""")

    countItems(project, "lists") should be(1)
    countItems(project, "todoes") should be(1)
  }

  "A nested upsert" should "execute the nested connect mutations of the correct create branch" in {
    dmPMToCm.testV11 { project =>
      server.query("""mutation { createTag(data:{uTag: "D"}){uTag}}""", project)
      server.query("""mutation {createList(data: {uList: "A"}){id}}""", project)

      server
        .query(
          s"""mutation{updateList(where:{uList: "A"}
             |                    data:{todoes: {
             |                        upsert:{
             |                               where:{uTodo: "B"}
             |		                           create:{uTodo:"C" tags: {connect: {uTag: "D"}}}
             |		                           update:{uTodo:{set:"Should Not Matter"},tags: {create: {uTag: "D"}}}
             |}}
             |}){id}}""".stripMargin,
          project
        )

      val result = server.query(s"""query{lists {uList, todoes {uTodo, tags {uTag }}}}""", project)
      result.toString should equal("""{"data":{"lists":[{"uList":"A","todoes":[{"uTodo":"C","tags":[{"uTag":"D"}]}]}]}}""")

      countItems(project, "lists") should be(1)
      countItems(project, "todoes") should be(1)
      countItems(project, "tags") should be(1)
    }

  }

  "A nested upsert" should "execute the nested connect mutations of the correct update branch" in {
    dmPMToCm.testV11 { project =>
      server.query("""mutation { createTag(data:{uTag: "D"}){uTag}}""", project)
      server.query("""mutation {createList(data: {uList: "A" todoes: {create: {uTodo: "B"}}}){id}}""", project)

      server
        .query(
          s"""mutation{updateList(where:{uList: "A"}
             |                    data:{todoes: {
             |                        upsert:{
             |                               where:{uTodo: "B"}
             |		                           create:{uTodo:"Should Not Matter" tags: {connect: {uTag: "D"}}}
             |		                           update:{uTodo: { set: "C" }, tags: { connect: { uTag: "D" }}}
             |}}
             |}){id}}""".stripMargin,
          project
        )

      val result = server.query(s"""query{lists {uList, todoes {uTodo, tags {uTag }}}}""", project)
      result.toString should equal("""{"data":{"lists":[{"uList":"A","todoes":[{"uTodo":"C","tags":[{"uTag":"D"}]}]}]}}""")

      countItems(project, "lists") should be(1)
      countItems(project, "todoes") should be(1)
      countItems(project, "tags") should be(1)
    }
  }

  //endregion

  def countItems(project: Project, name: String): Int = {
    server.query(s"""query{$name{id}}""", project).pathAsSeq(s"data.$name").length
  }
}
