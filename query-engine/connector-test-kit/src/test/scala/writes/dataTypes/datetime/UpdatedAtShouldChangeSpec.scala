package writes.dataTypes.datetime

import org.scalatest.{FlatSpec, Matchers}
import util._

class UpdatedAtShouldChangeSpec extends FlatSpec with Matchers with ApiSpecBase {

  override def runOnlyForCapabilities = Set(ConnectorCapability.ScalarListsCapability)

  val testDataModels = {
    def dm(scalarList: String) = s"""
      |model Top {
      |  id        String   @id @default(cuid())
      |  top       String   @unique
      |  createdAt DateTime @default(now())
      |  updatedAt DateTime @updatedAt
      |
      |  bottomId  String?
      |  bottom    Bottom?  @relation(fields: [bottomId], references: [id])
      |}
      |
      |model Bottom {
      |  id        String   @id @default(cuid())
      |  bottom    String   @unique
      |  createdAt DateTime @default(now())
      |  updatedAt DateTime @updatedAt
      |  
      |  top       Top?
      |}
      |
      |model List {
      |  id        String   @id @default(cuid())
      |  list      String   @unique
      |  ints      Int[]
      |  createdAt DateTime @default(now())
      |  updatedAt DateTime @updatedAt
      |}
      |"""

    TestDataModels(mongo = dm(""), sql = dm(""))
  }

  "Updating a data item" should "change it's updatedAt value" in {
    testDataModels.testV11 { project =>
      val updatedAt = server.query("""mutation a {createTop(data: { top: "top1" }) {updatedAt}}""", project).pathAsString("data.createTop.updatedAt")

      val changedUpdatedAt = server
        .query(
          s"""mutation b {
         |  updateTop(
         |    where: { top: "top1" }
         |    data: { top: { set: "top10" }}
         |  ) {
         |    updatedAt
         |  }
         |}
      """,
          project
        )
        .pathAsString("data.updateTop.updatedAt")

      updatedAt should not equal changedUpdatedAt
    }
  }

  "Upserting a data item" should "change it's updatedAt value" in {
    testDataModels.testV11 { project =>
      val updatedAt = server.query("""mutation a {createTop(data: { top: "top3" }) {updatedAt}}""", project).pathAsString("data.createTop.updatedAt")

      val changedUpdatedAt = server
        .query(
          s"""mutation b {
           |  upsertTop(
           |    where: { top: "top3" }
           |    update: { top: { set: "top30" }}
           |    create: { top: "Should not matter" }
           |  ) {
           |    updatedAt
           |  }
           |}
      """,
          project
        )
        .pathAsString("data.upsertTop.updatedAt")

      updatedAt should not equal changedUpdatedAt
    }
  }

  "UpdateMany" should "change updatedAt values" in {
    testDataModels.testV11 { project =>
      val updatedAt = server.query("""mutation a {createTop(data: { top: "top5" }) {updatedAt}}""", project).pathAsString("data.createTop.updatedAt")

      val res = server
        .query(
          s"""mutation b {
           |  updateManyTops(
           |    where: { top: { equals: "top5" }}
           |    data: { top: { set: "top50" }}
           |  ) {
           |    count
           |  }
           |}
      """,
          project
        )

      res.toString should be("""{"data":{"updateManyTops":{"count":1}}}""")

      val changedUpdatedAt = server
        .query(
          s"""query{
           |  top(where: { top: "top50" }) {
           |    updatedAt
           |  }
           |}
      """,
          project
        )
        .pathAsString("data.top.updatedAt")

      updatedAt should not equal changedUpdatedAt
    }
  }

  "Updating scalar list values" should "change updatedAt values" in {
    testDataModels.testV11 { project =>
      val updatedAt = server.query("""mutation a {createList(data: { list: "test" }) {updatedAt}}""", project).pathAsString("data.createList.updatedAt")

      val changedUpdatedAt = server
        .query(
          s"""mutation b {
           |  updateList(
           |    where: { list: "test" }
           |    data: { ints: {set: [1,2,3]}}
           |  ) {
           |    updatedAt
           |    ints
           |  }
           |}
      """,
          project
        )
        .pathAsString("data.updateList.updatedAt")

      updatedAt should not equal changedUpdatedAt
    }
  }

}
