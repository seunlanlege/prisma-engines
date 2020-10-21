package writes.dataTypes.datetime

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class WhereAndDateTimeSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  val project = SchemaDsl.fromStringV11() {
    s"""model Note{
      |   id            String   @id @default(cuid())
      |   outerString   String
      |   outerDateTime DateTime @unique
      |   todos         Todo[]   $relationInlineAttribute
      |}
      |
      |model Todo{
      |   id            String   @id @default(cuid())
      |   innerString   String
      |   innerDateTime DateTime @unique
      |   notes         Note[]
      |}"""
  }

  "Using the same input in an update using where as used during creation of the item" should "work" in {

    val outerWhere = """"2018-12-05T12:34:23.000Z""""
    val innerWhere = """"2019-12-05T12:34:23.000Z""""

    database.setup(project)

    server.query(
      s"""mutation {
         |  createNote(
         |    data: {
         |      outerString: "Outer String"
         |      outerDateTime: $outerWhere
         |      todos: {
         |        create: [
         |          { innerString: "Inner String", innerDateTime: $innerWhere }
         |        ]
         |      }
         |    }
         |  ){
         |    id
         |  }
         |}""".stripMargin,
      project
    )

    server.query(
      s"""
         |mutation {
         |  updateNote(
         |    where: { outerDateTime: $outerWhere }
         |    data: {
         |      outerString: { set: "Changed Outer String" }
         |      todos: {
         |        update: [{ 
         |          where: { innerDateTime: $innerWhere },
         |          data:{ innerString: { set: "Changed Inner String" }}
         |        }]
         |      }
         |    }
         |  ){
         |    id
         |  }
         |}
      """,
      project
    )

    server.query(
      s"""query{note(where:{outerDateTime:$outerWhere}){outerString, outerDateTime}}""",
      project,
      dataContains = """{"note":{"outerString":"Changed Outer String","outerDateTime":"2018-12-05T12:34:23.000Z"}}"""
    )

    server.query(
      s"""query{todo(where:{innerDateTime:$innerWhere}){innerString, innerDateTime}}""",
      project,
      dataContains = """{"todo":{"innerString":"Changed Inner String","innerDateTime":"2019-12-05T12:34:23.000Z"}}"""
    )
  }

  "Using the same input in an update using where as used during creation of the item" should "work with the same time for inner and outer" in {
    val outerWhere = """"2018-01-03T11:27:38.000Z""""
    val innerWhere = """"2018-01-03T11:27:38.000Z""""

    database.setup(project)

    val createResult = server.query(
      s"""mutation {
         |  createNote(
         |    data: {
         |      outerString: "Outer String"
         |      outerDateTime: $outerWhere
         |      todos: {
         |        create: [
         |          { innerString: "Inner String", innerDateTime: $innerWhere }
         |        ]
         |      }
         |    }
         |  ){
         |    id
         |  }
         |}""".stripMargin,
      project
    )

    server.query(
      s"""
         |mutation {
         |  updateNote(
         |    where: { outerDateTime: $outerWhere }
         |    data: {
         |      outerString: { set: "Changed Outer String" }
         |      todos: {
         |        update: [{
         |          where: { innerDateTime: $innerWhere }
         |          data:{ innerString: { set: "Changed Inner String" }}
         |        }]
         |      }
         |    }
         |  ){
         |    id,
         |    outerDateTime
         |
         |  }
         |}
      """.stripMargin,
      project
    )

    server.query(
      s"""query{note(where:{outerDateTime:$outerWhere}){outerString, outerDateTime}}""",
      project,
      dataContains = s"""{"note":{"outerString":"Changed Outer String","outerDateTime":"2018-01-03T11:27:38.000Z"}}"""
    )
    server.query(
      s"""query{todo(where:{innerDateTime:$innerWhere}){innerString, innerDateTime}}""",
      project,
      dataContains = s"""{"todo":{"innerString":"Changed Inner String","innerDateTime":"2018-01-03T11:27:38.000Z"}}"""
    )
  }

}
