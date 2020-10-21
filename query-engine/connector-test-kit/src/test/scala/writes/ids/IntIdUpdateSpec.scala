package writes.ids

import org.scalatest.{FlatSpec, Matchers}
import util._

class IntIdUpdateSpec extends FlatSpec with Matchers with ApiSpecBase {

  "Updating an item with an id field of type Int without default" should "work" in {
    val project = ProjectDsl.fromString {
      s"""
         |model Todo {
         |  id    Int @id
         |  title String
         |}
       """.stripMargin
    }
    database.setup(project)

    // Setup
    val res = server.query(
      s"""mutation {
         |  createTodo(data: {title: "initial", id: 12}) {title, id}
         |}""",
      project = project
    )

    res.toString should be(s"""{"data":{"createTodo":{"title":"initial","id":12}}}""")

    // Check
    val result = server.query(
      """
        |mutation {
        |  updateTodo(where: {id: 12}, data: {title: {set: "the title"}}){
        |    id
        |    title
        |  }
        |}
      """.stripMargin,
      project
    )

    result.pathAsString("data.updateTodo.title") should equal("the title")
    result.pathAsLong("data.updateTodo.id") should equal(12)
  }

  "Updating an item with an id field of type Int with static default" should "work" in {
    val project = ProjectDsl.fromString {
      s"""
         |model Todo {
         |  id    Int @id @default(0)
         |  title String
         |}
       """.stripMargin
    }

    database.setup(project)

    // Setup
    val res = server.query(
      s"""mutation {
         |  createTodo(data: {title: "initial", id: 12}) {title, id}
         |}""",
      project = project
    )

    res.toString should be(s"""{"data":{"createTodo":{"title":"initial","id":12}}}""")

    // Check
    val result = server.query(
      """
        |mutation {
        |  updateTodo(where: {id: 12}, data: { title: { set: "the title" }}){
        |    id
        |    title
        |  }
        |}
      """.stripMargin,
      project
    )

    result.pathAsString("data.updateTodo.title") should equal("the title")
    result.pathAsLong("data.updateTodo.id") should equal(12)
  }

  "Updating an item with an id field of type Int with autoincrement" should "work" in {
    val project = ProjectDsl.fromString {
      s"""
         |model Todo {
         |  id    Int @id @default(autoincrement())
         |  title String
         |}
       """.stripMargin
    }
    database.setup(project)

    // Setup
    val res = server.query(
      s"""mutation {
         |  createTodo(data: {title: "initial"}) {title, id}
         |}""",
      project = project
    )

    res.toString should be(s"""{"data":{"createTodo":{"title":"initial","id":1}}}""")

    // Check
    val result = server.query(
      """
        |mutation {
        |  updateTodo(where: {id: 1}, data: {title: {set: "the title"}}){
        |    id
        |    title
        |  }
        |}
      """.stripMargin,
      project
    )

    result.pathAsString("data.updateTodo.title") should equal("the title")
    result.pathAsLong("data.updateTodo.id") should equal(1)
  }

  "Updating the id of an item with an id field of type Int with autoincrement" should "error" in {
    val project = ProjectDsl.fromString {
      s"""
         |model Todo {
         |  id    Int @id @default(autoincrement())
         |  title String
         |}
       """.stripMargin
    }
    database.setup(project)

    // Setup
    val res = server.query(
      s"""mutation {
         |  createTodo(data: {title: "initial"}) {title, id}
         |}""",
      project = project
    )

    res.toString should be(s"""{"data":{"createTodo":{"title":"initial","id":1}}}""")

    // Check
    server.queryThatMustFail(
      """
        |mutation {
        |  updateTodo(where: {id: 1}, data: { title: { set: "the title" }, id: { set: 2 }}){
        |    id
        |    title
        |  }
        |}
      """.stripMargin,
      project,
      errorCode = 2009,
      errorContains = "`Field does not exist on enclosing type.` at `Mutation.updateTodo.data.TodoUpdateInput.id`"
    )
  }

  "Updating a unique field of type Int with autoincrement" should "error" taggedAs (IgnoreSQLite, IgnoreMySql) in {
    val project = ProjectDsl.fromString {
      s"""
         |model Todo {
         |  id         String @id
         |  counter    Int @unique @default(autoincrement())
         |  title      String
         |}
       """.stripMargin
    }
    database.setup(project)

    // Setup
    val res = server.query(
      s"""mutation {
         |  createTodo(data: {id: "the-id", title: "initial"}) {title, id, counter}
         |}""",
      project = project
    )

    res.toString should be(s"""{"data":{"createTodo":{"title":"initial","id":"the-id","counter":1}}}""")

    // Check
    server.queryThatMustFail(
      """
        |mutation {
        |  updateTodo(where: {id: "the-id"}, data: { counter: { set: 7 }}){
        |    id
        |    title
        |    counter
        |  }
        |}
      """.stripMargin,
      project,
      errorCode = 2009,
      errorContains = "`Field does not exist on enclosing type.` at `Mutation.updateTodo.data.TodoUpdateInput.counter`"
    )
  }

  "Updating a non-unique field of type Int with autoincrement" should "work" taggedAs (IgnoreSQLite, IgnoreMySql, IgnoreMsSql) in {
    val project = ProjectDsl.fromString {
      s"""
         |model Todo {
         |  id         String @id
         |  counter    Int @default(autoincrement())
         |  title      String
         |}
       """.stripMargin
    }
    database.setup(project)

    // Setup
    val res = server.query(
      s"""mutation {
         |  createTodo(data: {id: "the-id", title: "initial"}) {title, id, counter}
         |}""",
      project = project
    )

    res.toString should be(s"""{"data":{"createTodo":{"title":"initial","id":"the-id","counter":1}}}""")

    // Check
    val result = server.query(
      """
        |mutation {
        |  updateTodo(where: {id: "the-id"}, data: {title: { set: "the title" }, counter: { set: 8 }}){
        |    id
        |    title
        |    counter
        |  }
        |}
      """.stripMargin,
      project
    )

    result.pathAsString("data.updateTodo.title") should equal("the title")
    result.pathAsLong("data.updateTodo.counter") should equal(8)
  }

}
