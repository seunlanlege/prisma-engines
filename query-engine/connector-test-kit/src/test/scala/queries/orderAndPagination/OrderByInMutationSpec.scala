package queries.orderAndPagination

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class OrderByInMutationSpec extends FlatSpec with Matchers with ApiSpecBase {

  override def runOnlyForCapabilities: Set[ConnectorCapability] = Set(JoinRelationLinksCapability)

  val testDataModels = {
    val s1 = """
      model Foo {
          id   String  @id @default(cuid())
          test String?

          bars Bar[]
      }

      model Bar {
          id         String @id @default(cuid())
          quantity   Int
          orderField Int?
          foo_id     String

          foo Foo @relation(fields: [foo_id], references: [id])
      }
    """

    val s2 = """
      model Foo {
           id   String  @id @default(cuid())
           test String?

           bars Bar[]
       }

       model Bar {
           id         String @id @default(cuid())
           quantity   Int
           orderField Int?
           foo_id     String

           foo Foo @relation(fields: [foo_id], references: [id])
       }
    """

    TestDataModels(mongo = Vector(s1), sql = Vector(s2))
  }

  "Using a field in the order by that is not part of the selected fields" should "work" in {
    testDataModels.testV11 { project =>
      val res = server.query(
        """mutation {
        |  createOneFoo(
        |    data: {
        |      bars: {
        |        create: [
        |          { quantity: 1, orderField: 1}
        |          { quantity: 2, orderField: 2}
        |        ]
        |      }
        |    }
        |  ) {
        |    test
        |    bars(take: 1, orderBy: { orderField: desc }) {
        |      quantity
        |    }
        |  }
        |}
      """,
        project,
        legacy = false,
      )

      res.toString should be("""{"data":{"createOneFoo":{"test":null,"bars":[{"quantity":2}]}}}""")
    }
  }
}
