package writes.topLevelMutations

import org.scalatest.{FlatSpec, Matchers}
import util._

class DeleteManySpec extends FlatSpec with Matchers with ApiSpecBase {

  val project = ProjectDsl.fromString {
    """model Todo {
      |  id    String @id @default(cuid())
      |  title String
      |}
    """.stripMargin
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    database.setup(project)
  }

  override def beforeEach(): Unit = database.truncateProjectTables(project)

  "The delete many Mutation" should "delete the items matching the where clause" in {
    createTodo("title1")
    createTodo("title2")
    todoCountShouldBe(2)

    val result = server.query(
      """mutation {
        |  deleteManyTodoes(
        |    where: { title: { equals: "title1" }}
        |  ){
        |    count
        |  }
        |}
      """.stripMargin,
      project
    )
    result.pathAsLong("data.deleteManyTodoes.count") should equal(1)

    todoCountShouldBe(1)
  }

  "The delete many Mutation" should "delete all items if the where clause is empty" in {
    createTodo("title1")
    createTodo("title2")
    createTodo("title3")
    todoCountShouldBe(3)

    val result = server.query(
      """mutation {
        |  deleteManyTodoes(
        |    where: { }
        |  ){
        |    count
        |  }
        |}
      """.stripMargin,
      project
    )
    result.pathAsLong("data.deleteManyTodoes.count") should equal(3)

    todoCountShouldBe(0)
  }

  "The delete many Mutation" should "delete all items using in" in {
    createTodo("title1")
    createTodo("title2")
    createTodo("title3")

    val result = server.query(
      """mutation {
        |  deleteManyTodoes(
        |    where: { title: { in: [ "title1", "title2" ] }}
        |  ){
        |    count
        |  }
        |}
      """.stripMargin,
      project
    )
    result.pathAsLong("data.deleteManyTodoes.count") should equal(2)

    todoCountShouldBe(1)

  }

  "The delete many Mutation" should "delete all items using notin" in {
    createTodo("title1")
    createTodo("title2")
    createTodo("title3")

    val result = server.query(
      """mutation {
        |  deleteManyTodoes(
        |    where: { title: { not: { in: [ "DoesNotExist", "AlsoDoesntExist" ] }}}
        |  ){
        |    count
        |  }
        |}
      """.stripMargin,
      project
    )
    result.pathAsLong("data.deleteManyTodoes.count") should equal(3)

    todoCountShouldBe(0)
  }

  "The delete many Mutation" should "delete items using  OR" taggedAs (IgnoreMongo) in {
    createTodo("title1")
    createTodo("title2")
    createTodo("title3")

    val query = server.query(
      """query {
        |  todoes(
        |    where: { OR: [{title: { equals: "title1" }}, { title: { equals: "title2" }}]}
        |  ) {
        |    title
        |  }
        |}
      """.stripMargin,
      project
    )

    query.toString should be("""{"data":{"todoes":[{"title":"title1"},{"title":"title2"}]}}""")

    val result = server.query(
      """mutation {
        |  deleteManyTodoes(
        |    where: { OR: [{ title: { equals: "title1" }}, { title: { equals: "title2" }}]}
        |  ){
        |    count
        |  }
        |}
      """.stripMargin,
      project
    )
    result.pathAsLong("data.deleteManyTodoes.count") should equal(2)

    todoCountShouldBe(1)
  }

  "The delete many Mutation" should "delete items using  AND" in {
    createTodo("title1")
    createTodo("title2")
    createTodo("title3")

    val query = server.query(
      """query {
        |  todoes(
        |    where: { AND: [{ title: { equals: "title1" }}, { title: { equals: "title2" }}]}
        |  ){
        |    title
        |  }
        |}
      """.stripMargin,
      project
    )

    query.toString should be("""{"data":{"todoes":[]}}""")

    val result = server.query(
      """mutation {
        |  deleteManyTodoes(
        |    where: { AND: [{ title: { equals: "title1" }}, { title: { equals: "title2" }}]}
        |  ){
        |    count
        |  }
        |}
      """.stripMargin,
      project
    )
    result.pathAsLong("data.deleteManyTodoes.count") should equal(0)

    todoCountShouldBe(3)
  }

  "nested DeleteMany" should "work" in {

    val project = ProjectDsl.fromString {
      """
        |model ZChild{
        |    id       String  @id @default(cuid())
        |    name     String? @unique
        |    test     String?
        |    parentId String?
        |
        |    parent Parent? @relation(fields: [parentId], references: [id])
        |}
        |
        |model Parent{
        |    id       String  @id @default(cuid())
        |    name     String? @unique
        |    children ZChild[]
        |}"""
    }

    database.setup(project)

    val create = server.query(
      s"""mutation {
         |   createParent(data: {
         |   name: "Dad",
         |   children: {create:[{ name: "Daughter"},{ name: "Daughter2"}, { name: "Son"},{ name: "Son2"}]}
         |}){
         |  name,
         |  children{ name}
         |}}""",
      project
    )

    create.toString should be(
      """{"data":{"createParent":{"name":"Dad","children":[{"name":"Daughter"},{"name":"Daughter2"},{"name":"Son"},{"name":"Son2"}]}}}""")

    server.query(
      s"""mutation {
         |   updateParent(
         |   where: { name: "Dad" }
         |   data: {  children: {deleteMany:[
         |      {
         |          name: { contains: "Daughter" }
         |      },
         |      {
         |          name: { contains: "Son" }
         |      }
         |   ]
         |  }}
         |){
         |  name,
         |  children{ name}
         |}}""",
      project
    )
  }

  def todoCount: Int = {
    val result = server.query(
      "{ todoes { id } }",
      project
    )
    result.pathAsSeq("data.todoes").size
  }

  def todoCountShouldBe(int: Int) = {
    val result = server.query(
      "{ todoes { id } }",
      project
    )
    result.pathAsSeq("data.todoes").size should be(int)

  }

  def createTodo(title: String): Unit = {
    server.query(
      s"""mutation {
        |  createTodo(
        |    data: {
        |      title: "$title"
        |    }
        |  ) {
        |    id
        |  }
        |}
      """.stripMargin,
      project
    )
  }
}
