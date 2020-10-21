package queries.filters

import org.scalatest._
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class OneRelationFilterSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  val project = ProjectDsl.fromString {
    """
      |model Blog {
      |   id   String @id @default(cuid())
      |   name String
      |
      |   post Post?
      |}
      |
      |model Post {
      |   id         String  @id @default(cuid())
      |   title      String
      |   popularity Int
      |   blogId     String?
      |
      |   blog       Blog?    @relation(fields: [blogId], references: [id])
      |   comment    Comment?
      |
      |   @@index([blogId])
      |}
      |
      |model Comment {
      |   id     String  @id @default(cuid())
      |   text   String
      |   likes  Int
      |   postId String?
      |
      |   post  Post?   @relation(fields: [postId], references: [id])
      |
      |   @@index([postId])
      |}
    """
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    database.setup(project)
  }

  override def beforeEach() = {
    super.beforeEach()
    database.truncateProjectTables(project)

    // add data
    server.query(
      """mutation {createBlog(
        |     data: {
        |       name: "blog 1",
        |       post:{create: {title: "post 1", popularity: 10, comment:{ create: {text:"comment 1", likes: 10 }}}}
        | }
        |){name}}""",
      project
    )

    server.query(
      """mutation {createBlog(data:{
        |                         name: "blog 2",
        |                         post: {create:{title: "post 2",popularity: 100,comment:{create:{text:"comment 2", likes: 100}}}}
        |}){name}}""",
      project
    )

    server.query(
      """mutation {createBlog(data:{
        |                         name: "blog 3",
        |                         post: {create:{title: "post 3",popularity: 1000,comment:{create:{text:"comment 3", likes: 1000}}}}
        |}){name}}""",
      project
    )

  }

  "Scalar filter" should "work" in {
    server.query(query = """{posts(where: { title: {equals: "post 2"}}){title}}""", project).toString should be("""{"data":{"posts":[{"title":"post 2"}]}}""")
  }

  "1 level 1-relation filter" should "work" in {
    server.query(query = """{posts(where:{blog:{is:{name:{equals: "blog 1"}}}}){title}}""", project).toString should be(
      """{"data":{"posts":[{"title":"post 1"}]}}""")

    server.query(query = """{blogs(where:{post:{is:{popularity: { gte: 100 }}}}){name}}""", project).toString should be(
      """{"data":{"blogs":[{"name":"blog 2"},{"name":"blog 3"}]}}""")

    server.query(query = """{blogs(where:{post:{is:{popularity: { gte: 500 }}}}){name}}""", project).toString should be(
      """{"data":{"blogs":[{"name":"blog 3"}]}}""")

    server.query(query = """{blogs(where:{post:{isNot:{popularity: { gte: 500 }}}}){name}}""", project).toString should be(
      """{"data":{"blogs":[{"name":"blog 1"},{"name":"blog 2"}]}}""")

    server.query("""mutation { createOnePost(data: { title: "Post 4" popularity: 5 }) { title } }""", project, legacy = false)

    server.query(query = """{posts(where:{blog: { is: null}}){title}}""", project).toString should be("""{"data":{"posts":[{"title":"Post 4"}]}}""")

    server.query(query = """{posts(where:{blog: { isNot: null}}){title}}""", project).toString should be(
      """{"data":{"posts":[{"title":"post 1"},{"title":"post 2"},{"title":"post 3"}]}}""")
  }

  "1 level 1-relation filter" should "allow implicit `is` and allow nulls (if the field is optional)" in {
    server.query(query = """{posts(where: { blog: { name: { equals: "blog 1" }}}){title}}""", project).toString should be(
      """{"data":{"posts":[{"title":"post 1"}]}}""")

    server.query(query = """{blogs(where: { post: { popularity: { gte: 100 }}}){name}}""", project).toString should be(
      """{"data":{"blogs":[{"name":"blog 2"},{"name":"blog 3"}]}}""")

    server.query(query = """{blogs(where: { post: { popularity: { gte: 500 }}}){name}}""", project).toString should be(
      """{"data":{"blogs":[{"name":"blog 3"}]}}""")

    server.query(query = """{posts(where: { blog: { name: { equals: "blog 1" }}}){title}}""", project).toString should be(
      """{"data":{"posts":[{"title":"post 1"}]}}""")

    server.query("""mutation { createOnePost(data: { title: "Post 4" popularity: 5 }) { title } }""", project, legacy = false)

    server.query(query = """{posts(where: { blog: null }){title}}""", project).toString should be("""{"data":{"posts":[{"title":"Post 4"}]}}""")
  }

  "2 level 1-relation filter" should "work" in {
    server.query(query = """{blogs(where:{post:{is:{comment: {is:{likes: {equals:10}}}}}}){name}}""", project).toString should be(
      """{"data":{"blogs":[{"name":"blog 1"}]}}""")

    server.query(query = """{blogs(where:{post:{is:{comment:{is:{likes:{equals:1000}}}}}}){name}}""", project).toString should be(
      """{"data":{"blogs":[{"name":"blog 3"}]}}""")
  }

  "crazy filters" should "work" in {
    server
      .query(
        query = """{posts(where: {
                |  blog: {is: {
                |    post: {is: {
                |      popularity: { gte: 10 }
                |    }}
                |    name: { contains: "blog 1" }
                |  }}
                |  comment: {is: {
                |    likes: { gte: 5 }
                |    likes: { lte: 200 }
                |  }}
                |}) {
                |  title
                |}}""".stripMargin,
        project
      )
      .toString should be("""{"data":{"posts":[{"title":"post 1"}]}}""")
  }

  "Join Relation Filter on one to one relation" should "work on one level" taggedAs (IgnoreMsSql) in {
    val project = ProjectDsl.fromString {
      """
        |model Post {
        |  id      String @id @default(cuid())
        |  title   String @unique
        |
        |  author  AUser?
        |}
        |
        |model AUser {
        |  id     String  @id @default(cuid())
        |  name   String  @unique
        |  int    Int?
        |  postId String?
        |
        |  post  Post?   @relation(fields: [postId], references: [id])
        |}"""
    }

    database.setup(project)

    server.query(s""" mutation {createPost(data: {title:"Title1"}) {title}} """, project)
    server.query(s""" mutation {createPost(data: {title:"Title2"}) {title}} """, project)
    server.query(s""" mutation {createAUser(data: {name:"Author1", int: 5}) {name}} """, project)
    server.query(s""" mutation {createAUser(data: {name:"Author2", int: 4}) {name}} """, project)

    server.query(s""" mutation {updateAUser(where: { name: "Author1"}, data:{post:{connect:{title: "Title1"}}}) {name}} """, project)
    server.query(s""" mutation {updateAUser(where: { name: "Author2"}, data:{post:{connect:{title: "Title2"}}}) {name}} """, project)

    server.query("""query{aUsers{name, post{title}}}""", project).toString should be(
      """{"data":{"aUsers":[{"name":"Author1","post":{"title":"Title1"}},{"name":"Author2","post":{"title":"Title2"}}]}}""")

    server.query("""query{posts {title, author {name}}}""", project).toString should be(
      """{"data":{"posts":[{"title":"Title1","author":{"name":"Author1"}},{"title":"Title2","author":{"name":"Author2"}}]}}""")

    val res =
      server.query("""query{aUsers(where:{ post: {is:{title: { endsWith: "1" }}}, name: { startsWith: "Author" }, int: { equals: 5}}){name, post{title}}}""",
                   project)
    res.toString should be("""{"data":{"aUsers":[{"name":"Author1","post":{"title":"Title1"}}]}}""")
  }
}
