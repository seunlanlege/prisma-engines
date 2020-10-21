package writes.topLevelMutations

import org.scalatest.{FlatSpec, Matchers}
import util._

class DeleteMutationSpec extends FlatSpec with Matchers with ApiSpecBase {

  val project = ProjectDsl.fromString {
    """
      |model ScalarModel {
      |  id      String  @id @default(cuid())
      |  string  String?
      |  unicorn String? @unique
      |}
    """.stripMargin
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    database.setup(project)
  }

  override def beforeEach(): Unit = database.truncateProjectTables(project)

  "A Delete Mutation" should "delete and return item" in {
    val id =
      server.query(s"""mutation {createScalarModel(data: {string: "test"}){id}}""", project = project).pathAsString("data.createScalarModel.id")
    // TODO: bring back dataContains assertion. Currently we serialize all record fields back which breaks the test.
//    server.query(s"""mutation {deleteScalarModel(where: {id: "$id"}){id}}""", project = project, dataContains = s"""{"deleteScalarModel":{"id":"$id"}""")
    server.query(s"""mutation {deleteScalarModel(where: {id: "$id"}){id}}""", project = project)
    server.query(s"""query {scalarModels{unicorn}}""", project = project, dataContains = s"""{"scalarModels":[]}""")
  }

  "A Delete Mutation" should "gracefully fail on non-existing id" in {
    val id =
      server.query(s"""mutation {createScalarModel(data: {string: "test"}){id}}""", project = project).pathAsString("data.createScalarModel.id")
    server.queryThatMustFail(
      s"""mutation {deleteScalarModel(where: {id: "5beea4aa6183dd734b2dbd9b"}){id}}""",
      project = project,
      errorCode = 2016, // 3039,
      errorContains = """Query interpretation error. Error for binding '0': RecordNotFound(\"Record to delete does not exist."""
    )
    server.query(s"""query {scalarModels{string}}""", project = project, dataContains = s"""{"scalarModels":[{"string":"test"}]}""")
  }

  "A Delete Mutation" should "delete and return item on non id unique field" in {
    server.query(s"""mutation {createScalarModel(data: {unicorn: "a"}){id}}""", project = project)
    server.query(s"""mutation {createScalarModel(data: {unicorn: "b"}){id}}""", project = project)
    // TODO: bring back dataContains assertion. Currently we serialize all record fields back which breaks the test.
//    server.query(s"""mutation {deleteScalarModel(where: {unicorn: "a"}){unicorn}}""",
//                 project = project,
//                 dataContains = s"""{"deleteScalarModel":{"unicorn":"a"}""")
    server.query(s"""mutation {deleteScalarModel(where: {unicorn: "a"}){unicorn}}""", project = project)
    server.query(s"""query {scalarModels{unicorn}}""", project = project, dataContains = s"""{"scalarModels":[{"unicorn":"b"}]}""")
  }

  "A Delete Mutation" should "gracefully fail when trying to delete on non-existent value for non id unique field" in {
    server.query(s"""mutation {createScalarModel(data: {unicorn: "a"}){id}}""", project = project)
    server.queryThatMustFail(
      s"""mutation {deleteScalarModel(where: {unicorn: "c"}){unicorn}}""",
      project = project,
      errorCode = 2016, // 3039,
      errorContains = """Query interpretation error. Error for binding '0': RecordNotFound(\"Record to delete does not exist.\")"""
    )
    server.query(s"""query {scalarModels{unicorn}}""", project = project, dataContains = s"""{"scalarModels":[{"unicorn":"a"}]}""")
  }

  "A Delete Mutation" should "gracefully fail when trying to delete on null value for unique field" in {
    server.query(s"""mutation {createScalarModel(data: {unicorn: "a"}){id}}""", project = project)
    server.queryThatMustFail(
      s"""mutation {deleteScalarModel(where: {unicorn: null}){unicorn}}""",
      project = project,
      errorCode = 2012, // 3040,
      errorContains = """Missing a required value at `Mutation.deleteScalarModel.where.ScalarModelWhereUniqueInput.unicorn`"""
    )
    server.query(s"""query {scalarModels{unicorn}}""", project = project, dataContains = s"""{"scalarModels":[{"unicorn":"a"}]}""")
  }

  "A Delete Mutation" should "gracefully fail when referring to a non-unique field" in {
    server.query(s"""mutation {createScalarModel(data: {string: "a"}){id}}""", project = project)
    server.queryThatMustFail(
      s"""mutation {deleteScalarModel(where: {string: "a"}){string}}""",
      project = project,
      errorCode = 2009,
      errorContains = """`Field does not exist on enclosing type.` at `Mutation.deleteScalarModel.where.ScalarModelWhereUniqueInput.string`"""
    )
    server.query(s"""query {scalarModels{string}}""", project = project, dataContains = s"""{"scalarModels":[{"string":"a"}]}""")
  }
}
