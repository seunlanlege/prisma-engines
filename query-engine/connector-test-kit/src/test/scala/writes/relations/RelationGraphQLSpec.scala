package writes.relations

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class RelationGraphQLSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  "One2One relations" should "only allow one item per side" taggedAs (IgnoreMsSql) in {
    val dms = {
      val dm1 = """model Owner{
                     id        String  @id @default(cuid())
                     ownerName String? @unique
                     catId     String?

                     cat       Cat?    @relation(fields: [catId], references: [id])
                  }

                  model Cat{
                     id       String  @id @default(cuid())
                     catName  String? @unique
                     owner    Owner?
                  }"""

      val dm2 = """model Owner{
                     id        String  @id @default(cuid())
                     ownerName String? @unique
                     cat       Cat?
                  }

                  model Cat{
                     id      String  @id @default(cuid())
                     catName String? @unique
                     ownerId String?

                     owner   Owner?  @relation(fields: [ownerId], references: [id])
                  }"""

      TestDataModels(mongo = Vector(dm1, dm2), sql = Vector(dm1, dm2))
    }
    dms.testV11 { project =>
      createItem(project, "Cat", "garfield")
      createItem(project, "Cat", "azrael")
      createItem(project, "Owner", "jon")
      createItem(project, "Owner", "gargamel")

      //set initial owner
      val res = server.query(
        """mutation { updateCat(
        |  where: {catName: "garfield"},
        |  data: {owner: {connect: {ownerName: "jon"}}}) {
        |    catName
        |    owner {
        |      ownerName
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res.toString should be("""{"data":{"updateCat":{"catName":"garfield","owner":{"ownerName":"jon"}}}}""")

      val res2 = server.query("""query{owner(where:{ownerName:"jon"}){ownerName, cat{catName}}}""", project)
      res2.toString should be("""{"data":{"owner":{"ownerName":"jon","cat":{"catName":"garfield"}}}}""")

      val res3 = server.query("""query{owner(where:{ownerName:"gargamel"}){ownerName, cat{catName}}}""", project)
      res3.toString should be("""{"data":{"owner":{"ownerName":"gargamel","cat":null}}}""")

      //change owner

      val res4 = server.query(
        """mutation {updateCat(where: {catName: "garfield"},
        |data: {owner: {connect: {ownerName: "gargamel"}}}) {
        |    catName
        |    owner {
        |      ownerName
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res4.toString should be("""{"data":{"updateCat":{"catName":"garfield","owner":{"ownerName":"gargamel"}}}}""")

      val res5 = server.query("""query{owner(where:{ownerName:"jon"}){ownerName, cat{catName}}}""", project)
      res5.toString should be("""{"data":{"owner":{"ownerName":"jon","cat":null}}}""")

      val res6 = server.query("""query{owner(where:{ownerName:"gargamel"}){ownerName, cat{catName}}}""", project)
      res6.toString should be("""{"data":{"owner":{"ownerName":"gargamel","cat":{"catName":"garfield"}}}}""")
    }
  }

  //Fixme this tests transactionality as well
  // FIXME: this does not work for any connector
  "Required One2One relations" should "throw an error if an update would leave one item without a partner" taggedAs (IgnoreMongo) ignore { // TODO: Remove when transactions are back
    val dms = {
      val dm1 = """model Owner{
                     id        String  @id @default(cuid())
                     ownerName String? @unique
                     cat       Cat     @relation(references: [id])
                  }

                  model Cat{
                     id      String  @id @default(cuid())
                     catName String? @unique
                     owner   Owner
                  }"""

      val dm2 = """model Owner{
                     id        String  @id @default(cuid())
                     ownerName String? @unique
                     cat       Cat
                  }

                  model Cat{
                     id      String  @id @default(cuid())
                     catName String? @unique
                     owner   Owner   @relation(references: [id])
                  }"""

      TestDataModels(mongo = Vector(dm1, dm2), sql = Vector(dm1, dm2))
    }
    dms.testV11(1) { project =>
      //set initial owner
      val res = server.query(
        """mutation {createOwner(
        |data: {ownerName: "jon", cat : {create: {catName: "garfield"}}}) {
        |    ownerName
        |    cat {
        |      catName
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res.toString should be("""{"data":{"createOwner":{"ownerName":"jon","cat":{"catName":"garfield"}}}}""")

      val res2 = server.query("""query{owner(where:{ownerName:"jon"}){ownerName, cat{catName}}}""", project)
      res2.toString should be("""{"data":{"owner":{"ownerName":"jon","cat":{"catName":"garfield"}}}}""")

      //create new owner and connect to garfield

      server.queryThatMustFail(
        """mutation {createOwner(
        |data: {ownerName: "gargamel", cat : {connect: {catName: "garfield"}}}) {
        |    ownerName
        |    cat {
        |      catName
        |    }
        |  }
        |}""",
        project,
        errorCode = 3042,
        errorContains = "The change you are trying to make would violate the required relation 'CatToOwner' between Cat and Owner"
      )

      val res5 = server.query("""query{owner(where:{ownerName:"jon"}){ownerName, cat{catName}}}""", project)
      res5.toString should be("""{"data":{"owner":{"ownerName":"jon","cat":{"catName":"garfield"}}}}""")

      val res6 = server.query("""query{owner(where:{ownerName:"gargamel"}){ownerName, cat{catName}}}""", project)
      res6.toString should be("""{"data":{"owner":null}}""")
    }
  }

  def createItem(project: Project, modelName: String, name: String): Unit = {
    modelName match {
      case "Cat"   => server.query(s"""mutation {createCat(data: {catName: "$name"}){id}}""", project)
      case "Owner" => server.query(s"""mutation {createOwner(data: {ownerName: "$name"}){id}}""", project)
    }
  }

  def countItems(project: Project, name: String): Int = {
    server.query(s"""query{$name{id}}""", project).pathAsSeq(s"data.$name").length
  }

}
