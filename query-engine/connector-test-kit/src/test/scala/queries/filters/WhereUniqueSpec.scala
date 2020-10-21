package queries.filters

import org.scalatest.{FlatSpec, Matchers}
import util._

class WhereUniqueSpec extends FlatSpec with Matchers with ApiSpecBase {

  val project: Project = ProjectDsl.fromString { """
                                                   |model User {
                                                   |  id     String @id @default(cuid())
                                                   |  unique Int    @unique
                                                   |  email  String @unique
                                                   |}""" }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    database.setup(project)

    server
      .query(
        s"""mutation{createUser(
           |  data: {
           |    unique:2,
           |    email: "test@test.com"
           |})
           |{id}
           |}
        """,
        project
      )
  }

  // We cannot express that we expect exactly one field in the WhereUniqueInput in GraphQL, we therefore will error at runtime
  // and hint at the correct way to use the api

  "Providing zero unique fields" should "error" in {
    server.queryThatMustFail(
      s"""query{user(where: {}){unique}}""",
      project,
      errorCode = 2009, // 3040,
      errorContains = """`Expected exactly one field to be present, got 0.` at `Query.user.where.UserWhereUniqueInput`""",
    )
  }

  "Providing 1 unique field" should "work" in {
    server.query(s"""query{user(where: {email: "test@test.com"}){unique}}""", project).toString should be("""{"data":{"user":{"unique":2}}}""")
  }

  "Providing more than 1 unique field" should "error" in {
    server.queryThatMustFail(
      s"""query{user(where: {id:"wrong", email: "test@test.com"}){unique}}""",
      project,
      errorCode = 2009, // 3045,
      errorContains = """`Expected exactly one field to be present, got 2.` at `Query.user.where.UserWhereUniqueInput`"""
      // """You provided more than one field for the unique selector on User. If you want that behavior you can use the many query and combine fields with AND / OR."""
    )
  }

  "Using two unique fields with the many query" should "work with an implicit AND" in {
    server.query(s"""query{users(where: {unique: { equals: 2 }, email: { equals: "test@test.com" }}){unique}}""", project).toString should be(
      """{"data":{"users":[{"unique":2}]}}""")
  }

  "Using two unique fields with the many query" should "work with an explicit AND" in {
    server.query(s"""query{users(where: {AND: [{ unique: { equals: 2 }}, { email: { equals: "test@test.com" }}]}){unique}}""", project).toString should be(
      """{"data":{"users":[{"unique":2}]}}""")
  }

  "Using two unique fields with the many query" should "work with an explicit OR" taggedAs (IgnoreMongo) in {
    server.query(s"""query{users(where: {OR: [{ unique: { equals: 2 }}, { email: { equals: "does not exist" }}]}){unique}}""", project).toString should be(
      """{"data":{"users":[{"unique":2}]}}""")
  }

  "Using two unique fields with the many query" should "work with an explicit OR 2" taggedAs (IgnoreMongo) in {
    server.query(s"""query{users(where: {OR: [{ unique: { equals: 24235 }}, { email: { equals: "test@test.com" }}]}){unique}}""", project).toString should be(
      """{"data":{"users":[{"unique":2}]}}""")
  }
}
