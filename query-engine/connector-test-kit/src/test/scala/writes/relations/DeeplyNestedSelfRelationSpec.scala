package writes.relations

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class DeeplyNestedSelfRelationSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  "A deeply nested self relation create" should "be executed completely" in {
    val project = ProjectDsl.fromString {
      """model User {
      |  id       String  @id @default(cuid())
      |  name     String  @unique
      |  parentId String?
      |
      |  parent   User?  @relation(name: "Users", fields: [parentId], references: [id])
      |  children User[] @relation(name: "Users")
      |}"""
    }

    database.setup(project)

    val create = server.query(
      """mutation {
                   |  createUser(
                   |    data: {
                   |      name: "A"
                   |      children: {
                   |        create: [
                   |          { name: "B",
                   |            children: {
                   |              create: [{ name: "C" }]
                   |            }
                   |        }]
                   |      }
                   |    }
                   |  ) {
                   |    name
                   |    parent {name}
                   |    children {
                   |      name
                   |      parent {name}
                   |      children {
                   |        name
                   |        parent {name}
                   |        children {
                   |          parent {name}
                   |          id
                   |        }
                   |      }
                   |    }
                   |  }
                   |}""",
      project
    )

    create.toString should be(
      """{"data":{"createUser":{"name":"A","parent":null,"children":[{"name":"B","parent":{"name":"A"},"children":[{"name":"C","parent":{"name":"B"},"children":[]}]}]}}}""")

    val query = server.query("""{
                   |  users{
                   |    name
                   |  }
                   |}
                   |""",
                             project)

    query.toString should be("""{"data":{"users":[{"name":"A"},{"name":"B"},{"name":"C"}]}}""")

  }

  // TODO: bring `onDelete` back once `prisma migrate` is a thing
  "Regression #249" should "not fail" ignore {
    val project = SchemaDsl.fromStringV11() { """
                                                |model User {
                                                |  id       String @id @default(cuid())
                                                |  name     String
                                                |  comments Comment[]
                                                |}
                                                |
                                                |model Comment {
                                                |  id         String    @id @default(cuid())
                                                |  createdAt  DateTime  @default(now())
                                                |  updatedAt  DateTime  @updatedAt
                                                |  value      String
                                                |  authorId   String
                                                |
                                                |  author     User      @relation(...)
                                                |  children   Comment[] @relation("comment_children", onDelete: CASCADE)
                                                |}
                                              """.stripMargin }
    database.setup(project)

    val commentId = server
      .query(
        s"""mutation {
           |  createComment(data: {
           |    value: "Test"
           |    author: {
           |      create: {
           |        name: "Big Bird"
           |      }
           |    }
           |  }) {
           |    id
           |  }
           |}""".stripMargin,
        project
      )
      .pathAsString("data.createComment.id")

    val otherUserId = server
      .query(
        s"""mutation {
           |  createUser(data: {
           |    name: "Cookie Monster"
           |  }) {
           |    id
           |  }
           |}""".stripMargin,
        project
      )
      .pathAsString("data.createUser.id")

    val result = server
      .query(
        s"""mutation {
           |  updateComment(where: {
           |    id: "$commentId"
           |  }
           |  data: {
           |    children: {
           |      create: {
           |        value: "NOMNOMNOM"
           |          author: {
           |          connect: {
           |            id: "$otherUserId"
           |          }
           |        }
           |      }
           |    }
           |  }) {
           |    id
           |    children {
           |      author {
           |        id
           |      }
           |    }
           |  }
           |}""".stripMargin,
        project
      )

    result.pathAsSeq("data.updateComment.children").head.pathAsString("author.id") should equal(otherUserId)
  }
}
