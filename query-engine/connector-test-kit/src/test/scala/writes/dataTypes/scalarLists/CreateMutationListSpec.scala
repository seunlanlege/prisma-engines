package writes.dataTypes.scalarLists

import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.JsValue
import util.ConnectorCapability.ScalarListsCapability
import util._

class CreateMutationListSpec extends FlatSpec with Matchers with ApiSpecBase {

  override def runOnlyForCapabilities = Set(ScalarListsCapability)

  val project = ProjectDsl.fromString {
    s"""
      |model ScalarModel {
      |  id           String     @id @default(cuid())
      |  optStrings   String[]
      |  optInts      Int[]
      |  optFloats    Float[]
      |  optBooleans  Boolean[]
      |  optEnums     MyEnum[]
      |  optDateTimes DateTime[]
      |}
      |
      |enum MyEnum {
      |  A
      |  ABCD
      |}
    """.stripMargin
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    database.setup(project)
  }

  override def beforeEach(): Unit = database.truncateProjectTables(project)

  "A Create Mutation" should "create and return items with listvalues" in {
    val res = server.query(
      s"""mutation {
         |  createScalarModel(data: {
         |    optStrings: {set:["lala${TroubleCharacters.value}"]},
         |    optInts:{set: [1337, 12]},
         |    optFloats: {set:[1.234, 1.45]},
         |    optBooleans: {set:[true,false]},
         |    optEnums: {set:[A,A]},
         |    optDateTimes: {set:["2016-07-31T23:59:01.000Z","2017-07-31T23:59:01.000Z"]},
         |  }){optStrings, optInts, optFloats, optBooleans, optEnums, optDateTimes }
         |}""",
      project = project
    )

    res should be(
      s"""{"data":{"createScalarModel":{"optEnums":["A","A"],"optBooleans":[true,false],"optDateTimes":["2016-07-31T23:59:01.000Z","2017-07-31T23:59:01.000Z"],"optStrings":["lala${TroubleCharacters.value}"],"optInts":[1337,12],"optFloats":[1.234,1.45]}}}""".parseJson)

    val queryRes: JsValue =
      server.query("""{ scalarModels{optStrings, optInts, optFloats, optBooleans, optEnums, optDateTimes }}""", project = project)

    queryRes should be(
      s"""{"data":{"scalarModels":[{"optEnums":["A","A"],"optBooleans":[true,false],"optDateTimes":["2016-07-31T23:59:01.000Z","2017-07-31T23:59:01.000Z"],"optStrings":["lala${TroubleCharacters.value}"],"optInts":[1337,12],"optFloats":[1.234,1.45]}]}}""".parseJson)
  }

  "A Create Mutation" should "create and return items with listvalues with shorthand notation" in {
    val res = server.query(
      s"""mutation {
         |  createScalarModel(data: {
         |    optStrings: ["lala${TroubleCharacters.value}"],
         |    optInts: [1337, 12],
         |    optFloats: [1.234, 1.45],
         |    optBooleans: [true,false],
         |    optEnums: [A,A],
         |    optDateTimes: ["2016-07-31T23:59:01.000Z","2017-07-31T23:59:01.000Z"],
         |  }){optStrings, optInts, optFloats, optBooleans, optEnums, optDateTimes }
         |}""",
      project = project
    )

    res should be(
      s"""{"data":{"createScalarModel":{"optEnums":["A","A"],"optBooleans":[true,false],"optDateTimes":["2016-07-31T23:59:01.000Z","2017-07-31T23:59:01.000Z"],"optStrings":["lala${TroubleCharacters.value}"],"optInts":[1337,12],"optFloats":[1.234,1.45]}}}""".parseJson)

    val queryRes: JsValue =
      server.query("""{ scalarModels{optStrings, optInts, optFloats, optBooleans, optEnums, optDateTimes }}""", project = project)

    queryRes should be(
      s"""{"data":{"scalarModels":[{"optEnums":["A","A"],"optBooleans":[true,false],"optDateTimes":["2016-07-31T23:59:01.000Z","2017-07-31T23:59:01.000Z"],"optStrings":["lala${TroubleCharacters.value}"],"optInts":[1337,12],"optFloats":[1.234,1.45]}]}}""".parseJson)
  }

  "A Create Mutation" should "create and return items with empty listvalues" in {
    val res = server.query(
      s"""mutation {
         |  createScalarModel(data: {
         |    optStrings: {set:[]},
         |    optInts:{set: []},
         |    optFloats: {set:[]},
         |    optBooleans: {set:[]},
         |    optEnums: {set:[]},
         |    optDateTimes: {set:[]},
         |  }){optStrings, optInts, optFloats, optBooleans, optEnums, optDateTimes }
         |}""",
      project = project
    )

    res should be(
      s"""{"data":{"createScalarModel":{"optEnums":[],"optBooleans":[],"optDateTimes":[],"optStrings":[],"optInts":[], "optFloats":[]}}}""".parseJson)
  }

  "A Create Mutation with an empty scalar list update input object" should "return a detailed error" in {
    val res = server.queryThatMustFail(
      s"""mutation {
         |  createScalarModel(data: {
         |    optStrings: {},
         |  }){ optStrings, optInts, optFloats, optBooleans, optEnums, optDateTimes }
         |}""",
      project = project,
      errorCode = 2009,
      errorContains =
        """`Mutation.createScalarModel.data.ScalarModelCreateInput.optStrings.ScalarModelCreateoptStringsInput.set`: A value is required but not set."""
    )
  }

  "ListValues" should "work" in {
    val testDataModels = {
      val dm1 = s"""model Top {
                     id     String @id @default(cuid())
                     unique Int    @unique
                     name   String
                     ints   Int[]
                  }"""

      val dm2 = s"""model Top {
                     id     String @id @default(cuid())
                     unique Int    @unique
                     name   String
                     ints   Int[]
                  }"""

      TestDataModels(mongo = dm1, sql = dm2)
    }

    testDataModels.testV11 { project =>
      val res = server.query(
        s"""mutation {
           |   createTop(data: {
           |   unique: 1,
           |   name: "Top",
           |   ints: {set:[1,2,3,4,5]}
           |}){
           |  unique,
           |  ints
           |}}""",
        project
      )

      res.toString should be("""{"data":{"createTop":{"unique":1,"ints":[1,2,3,4,5]}}}""")
    }

  }
}
