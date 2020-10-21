package queries.regressions

import org.scalatest.{FlatSpec, Matchers}
import util._

class PaginationRegressionSpec extends FlatSpec with Matchers with ApiSpecBase {
  "[prisma/2855] Duplicate ordering keys on non-sequential IDs" should "still allow paging through records predictably" in {
    // ID on ModelB is non-sequential.
    val project = SchemaDsl.fromStringV11() {
      """
        |model ModelA {
        |  id Int @id
        |  bs ModelB[]
        |}
        |
        |model ModelB {
        |  id String @id
        |  createdAt DateTime @default(now())
        |  a_id Int
        |  a ModelA @relation(fields: [a_id], references: [id])
        |}
      """.stripMargin
    }

    database.setup(project)
    create_test_data_2855(project)

    val page1 = server.query(
      """
      |{
      |  findManyModelB(take: 5, orderBy: [{ createdAt: desc}, { id: asc }]) {
      |    id
      |    createdAt
      |  }
      |}
    """.stripMargin,
      project,
      legacy = false
    )

    page1.toString should equal(
      """{"data":{"findManyModelB":[{"id":"7e00aa78-5951-4c05-8e42-4edb0927e964","createdAt":"2020-06-25T20:05:38.000Z"},{"id":"84c01d52-838d-4cdd-9035-c09cf54a06a0","createdAt":"2020-06-25T19:44:50.000Z"},{"id":"3e7d6b95-c62d-4e66-bb8c-66a317386e40","createdAt":"2020-06-19T21:32:11.000Z"},{"id":"99f1734d-6ad1-4cf0-b851-2ed551cbabc6","createdAt":"2020-06-19T21:32:02.000Z"},{"id":"9505b8a9-45a1-4aae-a284-5bacfe9f835c","createdAt":"2020-06-19T21:31:51.000Z"}]}}""")

    val page2 = server.query(
      """
        |{
        |  findManyModelB(cursor: { id: "9505b8a9-45a1-4aae-a284-5bacfe9f835c" }, skip: 1, take: 5, orderBy: [{ createdAt: desc}, { id: asc }] ) {
        |    id
        |    createdAt
        |  }
        |}
      """.stripMargin,
      project,
      legacy = false
    )

    page2.toString should equal(
      """{"data":{"findManyModelB":[{"id":"ea732052-aac6-429b-84ea-976ca1f645d0","createdAt":"2020-06-11T22:34:15.000Z"},{"id":"13394728-24a6-4a37-aa6e-369e7f70c10b","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"16fa1ce3-5243-4a30-970e-8ec98d077810","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"36e88f2e-9f4c-4e26-9add-fbf76e404959","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"3c0f269f-0796-427e-af67-8c1a99f3524d","createdAt":"2020-06-10T21:52:26.000Z"}]}}""")

    val page3 = server.query(
      """
        |{
        |  findManyModelB(cursor: { id: "3c0f269f-0796-427e-af67-8c1a99f3524d" }, skip: 1, take: 5, orderBy: [{ createdAt: desc}, { id: asc }] ) {
        |    id
        |    createdAt
        |  }
        |}
      """.stripMargin,
      project,
      legacy = false
    )

    page3.toString should equal(
      """{"data":{"findManyModelB":[{"id":"517e8f7f-980a-44bf-8500-4e279a120b72","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"620d09a6-f5bd-48b5-bbe6-d55fcf341392","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"755f5bba-25e3-4510-a991-e0cfe02d864d","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"8a49e477-1f12-4a81-953f-c7b0ca5696dc","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"8c7a3864-285c-4f06-9c9a-273e19e19a05","createdAt":"2020-06-10T21:52:26.000Z"}]}}""")

    val page4 = server.query(
      """
        |{
        |  findManyModelB(cursor: { id: "8c7a3864-285c-4f06-9c9a-273e19e19a05" }, skip: 1, take: 5, orderBy: [{ createdAt: desc}, { id: asc }] ) {
        |    id
        |    createdAt
        |  }
        |}
      """.stripMargin,
      project,
      legacy = false
    )

    page4.toString should equal(
      """{"data":{"findManyModelB":[{"id":"bae99648-bdad-440f-953b-ddab33c6ea0b","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"eb8c5a20-ae61-402b-830f-f9518957f195","createdAt":"2020-06-10T21:52:26.000Z"},{"id":"79066f5a-3640-42e9-be04-2a702924f4c6","createdAt":"2020-06-04T16:00:21.000Z"},{"id":"a4b0472a-52fc-4b2d-8c44-4c401c18f469","createdAt":"2020-06-03T21:13:57.000Z"},{"id":"fc34b132-e376-406e-ab89-10ee35b4d58d","createdAt":"2020-05-12T12:30:12.000Z"}]}}""")
  }

  "[prisma/3505][Case 1] Paging and ordering with potential null values ON a null row" should "still allow paging through records predictably" in {
    // "On null row" means that the cursor row contains a null value for the ordering field, in this case row 2.
    val project = SchemaDsl.fromStringV11() {
      """
      |model TestModel {
      |  id    Int     @id
      |  field String?
      |}
    """.stripMargin
    }

    database.setup(project)

    // 5 records with ids 1 to 5
    // Contain some nulls for `field`.
    server.query("""mutation { createOneTestModel(data: { id: 1 }) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 2 }) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 3, field: "Test"}) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 4 }) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 5, field: "Test2"}) { id }}""", project, legacy = false)

    // Selects the 2 records after ID 2.
    // There are 2 options, depending on how the underlying db orders NULLS (first or last, * ids have nulls in `field`):
    // Nulls last:  5, 3, 1*, 2*, 4* => take only 4
    // Nulls first: 1*, 2*, 4*, 5, 3 => take 4, 5
    val result = server.query(
      """
        |{
        |  findManyTestModel(
        |    cursor: { id: 2 },
        |    take: 2,
        |    skip: 1,
        |    orderBy: [{ field: desc }, { id: asc }]
        |  ) { id }
        |}
      """.stripMargin,
      project,
      legacy = false
    )

    Seq(
      """{"data":{"findManyTestModel":[{"id":4}]}}""",
      """{"data":{"findManyTestModel":[{"id":4},{"id":5}]}}"""
    ) should contain(result.toString())
  }

  "[prisma/3505][Case 2] Paging and ordering with potential null values NOT ON a null row" should "still allow paging through records predictably" in {
    // "Not on null row" means that the cursor row does not contain a null value for the ordering field, in this case row 2.
    // However, other rows might still have nulls, those must be taken into consideration.
    val project = SchemaDsl.fromStringV11() {
      """
        |model TestModel {
        |  id    Int     @id
        |  field String?
        |}
      """.stripMargin
    }

    database.setup(project)

    // 5 records with ids 1 to 5
    // Contain some nulls for `field`.
    server.query("""mutation { createOneTestModel(data: { id: 1 }) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 2, field: "Test" }) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 3 }) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 4 }) { id }}""", project, legacy = false)
    server.query("""mutation { createOneTestModel(data: { id: 5, field: "Test2"}) { id }}""", project, legacy = false)

    // Selects the 2 records after ID 5.
    // There are 2 options, depending on how the underlying db orders NULLS (first or last, * ids have nulls in `field`):
    // field DESC, id ASC
    // Nulls last: 5, 2, 1*, 3*, 4* => take 2, 1
    // Nulls first: 1*, 3*, 4*, 5, 2 => take 2
    val result = server.query(
      """
        |{
        |  findManyTestModel(
        |    cursor: { id: 5 },
        |    take: 2,
        |    skip: 1,
        |    orderBy: [{ field: desc }, { id: asc }]
        |  ) { id }
        |}
      """.stripMargin,
      project,
      legacy = false
    )

    Seq(
      """{"data":{"findManyTestModel":[{"id":2}]}}""",
      """{"data":{"findManyTestModel":[{"id":2},{"id":1}]}}"""
    ) should contain(result.toString())
  }

  def create_test_data_2855(project: Project): Unit = {
    server.query(
      """
        |mutation {
        |  createOneModelA(
        |    data: {
        |      id: 1
        |      bs: {
        |        create: [
        |          {
        |            id: "7e00aa78-5951-4c05-8e42-4edb0927e964"
        |            createdAt: "2020-06-25T20:05:38.000Z"
        |          }
        |          {
        |            id: "84c01d52-838d-4cdd-9035-c09cf54a06a0"
        |            createdAt: "2020-06-25T19:44:50.000Z"
        |          }
        |          {
        |            id: "3e7d6b95-c62d-4e66-bb8c-66a317386e40"
        |            createdAt: "2020-06-19T21:32:11.000Z"
        |          }
        |          {
        |            id: "99f1734d-6ad1-4cf0-b851-2ed551cbabc6"
        |            createdAt: "2020-06-19T21:32:02.000Z"
        |          }
        |          {
        |            id: "9505b8a9-45a1-4aae-a284-5bacfe9f835c"
        |            createdAt: "2020-06-19T21:31:51.000Z"
        |          }
        |          {
        |            id: "ea732052-aac6-429b-84ea-976ca1f645d0"
        |            createdAt: "2020-06-11T22:34:15.000Z"
        |          }
        |          {
        |            id: "13394728-24a6-4a37-aa6e-369e7f70c10b"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "16fa1ce3-5243-4a30-970e-8ec98d077810"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "36e88f2e-9f4c-4e26-9add-fbf76e404959"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "3c0f269f-0796-427e-af67-8c1a99f3524d"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "517e8f7f-980a-44bf-8500-4e279a120b72"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "620d09a6-f5bd-48b5-bbe6-d55fcf341392"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "755f5bba-25e3-4510-a991-e0cfe02d864d"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "8a49e477-1f12-4a81-953f-c7b0ca5696dc"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "8c7a3864-285c-4f06-9c9a-273e19e19a05"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "bae99648-bdad-440f-953b-ddab33c6ea0b"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "eb8c5a20-ae61-402b-830f-f9518957f195"
        |            createdAt: "2020-06-10T21:52:26.000Z"
        |          }
        |          {
        |            id: "79066f5a-3640-42e9-be04-2a702924f4c6"
        |            createdAt: "2020-06-04T16:00:21.000Z"
        |          }
        |          {
        |            id: "a4b0472a-52fc-4b2d-8c44-4c401c18f469"
        |            createdAt: "2020-06-03T21:13:57.000Z"
        |          }
        |          {
        |            id: "fc34b132-e376-406e-ab89-10ee35b4d58d"
        |            createdAt: "2020-05-12T12:30:12.000Z"
        |          }
        |        ]
        |      }
        |    }
        |  ) {
        |    id
        |  }
        |}
        |
      """.stripMargin,
      project,
      legacy = false
    )
  }
}
