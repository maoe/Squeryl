/*******************************************************************************
 * Copyright 2010 Maxime Lévesque
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.squeryl.tests.schooldb


import java.sql.SQLException
import org.squeryl.annotations.{Column}
import org.squeryl.tests.QueryTester
import java.util.Date
import java.text.SimpleDateFormat
import org.squeryl.dsl.{GroupWithMeasures}
import org.squeryl.dsl._
import ast.TypedExpressionNode
import org.squeryl._
import adapters.MySQLAdapter

class SchoolDbObject extends KeyedEntity[Int] {
  var id: Int = 0
}

class Student(var name: String, var lastName: String, var age: Option[Int], var gender: Int, var addressId: Option[Int], var isMultilingual: Option[Boolean])
  extends SchoolDbObject {

  def this() = this(null,null,Some(0),0, Some(0), Some(false))

  override def toString = "Student:" + id + ":" + name
}

case class Course(var name: String, var startDate: Date, var finalExamDate: Option[Date],
  @Column("meaninglessLongZ")
  var meaninglessLong: Long,
  @Column("meaninglessLongOption")
  var meaninglessLongOption: Option[Long], val confirmed: Boolean)
  extends SchoolDbObject with Optimistic {

  def occVersionNumberZ = occVersionNumber

  def this() = this("", null, Some(new Date), 0, Some(0), false)
  override def toString = "Course:" + id + ":" + name
}

class CourseSubscription(var courseId: Int, var studentId: Int)
  extends SchoolDbObject {

  def this() = this(0,0)
  override def toString = "CourseSubscription:" + id
}

class CourseAssignment(var courseId: Int, var professorId: Long)
  extends SchoolDbObject {

  def this() = this(0,0)
  override def toString = "CourseAssignment:" + id
}

class Address(var streetName: String, var numberz:Int, var numberSuffix:Option[String], var appNumber: Option[Int], var appNumberSuffix: Option[String])
  extends SchoolDbObject {

  def this() = this(null,0, Some(""),Some(0), Some(""))

  override def toString = "rue " + streetName 
}

class Professor(var lastName: String, var yearlySalary: Float, var weight: Option[Float], var yearlySalaryBD: BigDecimal, var weightInBD: Option[BigDecimal]) extends KeyedEntity[Long] {

  var id: Long = 0
  def this() = this("", 0.0F, Some(0.0F), 80.0F, Some(0))
  override def toString = "Professor:" + id

  import org.squeryl.PrimitiveTypeMode._
  
  //def assignments =
    //oneToMany(this, SDB.courseAssigments) ((p, ca) => p.id === ca.professorId)

  //def assign = Relation(this, SDB.courseAssigments) {}
//  def assignments =
//    One(this) ToMany(SDB.courseAssigments) on(p=>p.id, ca => ca.professorId)
}

object SDB extends SchoolDb

class SchoolDb extends Schema with QueryTester {

  import org.squeryl.PrimitiveTypeMode._

  override def columnNameFromPropertyName(n:String) =
    NamingConventionTransforms.camelCase2underScore(n)

  /**
   * Let's illustrate the support for crappy table naming convention !
   */
  override def tableNameFromClassName(n:String) =
    "T_" + n

  val professors = table[Professor]
  
  val students = table[Student]
  
  val addresses = table[Address]

  val courses = table[Course]

  val courseSubscriptions = table[CourseSubscription]

  val courseAssigments = table[CourseAssignment]
  
  val testInstance = new {

    drop

    create

    val oneHutchissonStreet = addresses.insert(new Address("Hutchisson",1, None,None,None))
    val twoHutchissonStreet = addresses.insert(new Address("Hutchisson",2, None,None,None))
    val oneTwoThreePieIXStreet = addresses.insert(new Address("Pie IX",123, None,Some(4),Some("A")))

    val xiao   = students.insert(new Student("Xiao", "Jimbao Gallois", Some(24), 2, Some(oneHutchissonStreet.id), Some(true)))
    val georgi = students.insert(new Student("Georgi", "Balanchivadze Fourrier", Some(52), 1, Some(oneHutchissonStreet.id), None))
    val pratap = students.insert(new Student("Pratap", "Jamsetji Bach", Some(25), 1, Some(oneTwoThreePieIXStreet.id), None))
    val gontran = students.insert(new Student("Gontran", "Plourde", Some(25), 1, Some(oneHutchissonStreet.id), Some(true)))
    val gaitan = students.insert(new Student("Gaitan", "Plouffe", Some(19), 1, None, Some(true)))

    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val jan2009 = dateFormat.parse("2009-01-01")
    val may2009 = dateFormat.parse("2009-05-01")
    val feb2009 = dateFormat.parse("2009-02-01")
    val feb2010 = dateFormat.parse("2010-02-01")
    val feb2011 = dateFormat.parse("2011-02-01")

    val groupTheory = courses.insert(new Course("Group Theory", jan2009, Some(may2009), 0, None, false))
    val heatTransfer = courses.insert(new Course("Heat Transfer", feb2009, None, 3, Some(1234), false))
    val counterpoint = courses.insert(new Course("Counterpoint", feb2010, None,0, None, true))
    val mandarin = courses.insert(new Course("Mandarin 101", feb2010, None, 0, None, true))

    courseSubscriptions.insert(new CourseSubscription(groupTheory.id, xiao.id))
    courseSubscriptions.insert(new CourseSubscription(heatTransfer.id, gontran.id))
    courseSubscriptions.insert(new CourseSubscription(heatTransfer.id, georgi.id))
    courseSubscriptions.insert(new CourseSubscription(counterpoint.id, pratap.id))
    courseSubscriptions.insert(new CourseSubscription(mandarin.id, gaitan.id))

    val tournesol = professors.insert(new Professor("tournesol", 80.0F, Some(70.5F), 80.0F, Some(70.5F)))

    Session.currentSession.connection.commit
  }

  def avgStudentAge =
    from(students)(s =>
      compute(avg(s.age))
    )

  def avgStudentAgeFunky =
    from(students)(s =>
      compute(avg(s.age), avg(s.age) + 3, avg(s.age) / count, count + 6)
    )

  def addressesOfStudentsOlderThan24 = 
    from(students, addresses)((s,a) =>
      where((24 : NumericalExpression[Int]) < s.age and (24 lt s.age))
      select(&(a.numberz || " " || a.streetName || " " || a.appNumber))
    )

  def fullOuterJoinStudentAddresses =
    from(students, addresses)((s,a) =>
      select(fullOuterJoin(s, a, s.addressId === a.id))
      orderBy(s.id)
    )

  def test1 = {

    //Must run first, because later we won't have the rows we need to perform the test

    if(Session.currentSession.databaseAdapter.isInstanceOf[MySQLAdapter]) {
      testOuterJoinMixed1
    }
    
    testBigDecimal
    
    testBatchUpdate1
    
    testBatchInserts1
    
    testPartialUpdate1
    
    testOptimisticCC1
    
    testNVLFunction
    testDateOptionComparisonInWhereClause
    testDateComparisonInWhereClause

    testMetaData
    
    testInstance
    //logQueries = true
    
    //exerciseTypeSystem1
    testForUpdate
    testFloatType
    testBooleanOptionMapping
    testBooleanTypeMapping
    
    testLongTypeMapping
    
    testDateTypeMapping
    testDateOptionMapping

    testScalarOptionQuery
    testOptionAndNonOptionMixInComputeTuple
    testConcatWithOptionalCols
    testLeftOuterJoin1

    if(Session.currentSession.databaseAdapter.isFullOuterJoinSupported)
      testFullOuterJoin1

    testLikeOperator
    testNotOperator

    drop
  }

  def testLeftOuterJoin1 {
    import testInstance._ 

    //loggerOn
    
    val leftOuterJoinStudentAddresses =
      from(students, addresses)((s,a) =>
        select((s,leftOuterJoin(a, s.addressId === a.id)))
        orderBy(s.id)
      )

    val res =
      (for(t <- leftOuterJoinStudentAddresses)
       yield (t._1.id, t._2.map(a=>a.id))).toList
    
    val expected = List(
      (xiao.id,Some(oneHutchissonStreet.id)),
      (georgi.id,Some(oneHutchissonStreet.id)),
      (pratap.id,Some(oneTwoThreePieIXStreet.id)),
      (gontran.id,Some(oneHutchissonStreet.id)),
      (gaitan.id,None))
    
    assert(expected == res, "expected :\n " + expected + "\ngot : \n " + res)
    
    println('testOuterJoin1 + " passed.")
  }

  def testFullOuterJoin1 {
    import testInstance._

    //println(fullOuterJoinStudentAddresses.dumpAst)
    //println(fullOuterJoinStudentAddresses)

    val res =
      (for(t <- fullOuterJoinStudentAddresses)
       yield (t._1.map(s=>s.id), t._2.map(a=>a.id))).toList

    val expected = List(
      (Some(xiao.id),Some(oneHutchissonStreet.id)),
      (Some(georgi.id),Some(oneHutchissonStreet.id)),
      (Some(pratap.id),Some(oneTwoThreePieIXStreet.id)),
      (Some(gontran.id),Some(oneHutchissonStreet.id)),
      (Some(gaitan.id),None),
      (None,Some(twoHutchissonStreet.id))
    )

    assert(expected == res, "expected :\n " + expected + "\ngot :\n " + res)

    println('testFullOuterJoin1 + " passed.")
  }

  def testOuterJoinMixed1 {
    import testInstance._

    //Creates a situation with two implicit inner joins and one outer join
    val studentsWithCoursesInFeb2010OuterJoinAdresses =
      from(students, courses, courseSubscriptions, addresses)((student, course, subscription, address) =>
        where(student.id === subscription.studentId and
              subscription.courseId === course.id and
              course.startDate === feb2010).
        select((student, course, leftOuterJoin(address, student.addressId === address.id))).
        orderBy(student.id)
      )

    val res: Seq[(Int, Int, Option[Int])] = studentsWithCoursesInFeb2010OuterJoinAdresses.map({
      case (student, course, address) =>
        (student.id, course.id, address.map(_.id))
    })(collection.breakOut)

    val expected = Seq(
      (pratap.id, counterpoint.id, Some(oneTwoThreePieIXStreet.id)),
      (gaitan.id, mandarin.id,     None)
    )

    assert(expected sameElements res, "expected :\n " + expected + "\ngot : \n " + res)

    println('testOuterJoinMixed1 + " passed.")
  }

  import testInstance._
  
  def testMetaData = {

    professors.posoMetaData.primaryKey
    
    val tst = new Student("Xiao", "Jimbao Gallois", Some(24), 2,Some(1), None)
    val fmd = addresses.posoMetaData.findFieldMetaDataForProperty("appNumberSuffix")
    assert(fmd.get.fieldType.isAssignableFrom(classOf[String]), "'FieldMetaData " + fmd + " should be of type java.lang.String")

    val pk = addresses.posoMetaData.primaryKey
    assert(pk != None, "MetaData of addresses should have 'id' as PK : \n" + addresses.posoMetaData)

    println('testMetaData + " passed.")
  }

  def testOptionAndNonOptionMixInComputeTuple = {
    val t:(Option[Float],Option[Float],Option[Double], Long) = avgStudentAgeFunky
    println('testOptionAndNonOptionMixInComputeTuple + " passed.")
  }

  def testConcatWithOptionalCols = {

    //loggerOn

    val res = addressesOfStudentsOlderThan24.toList
    
    println('testConcatWithOptionalCols + " passed.")
  }

  def testScalarOptionQuery = {
    val avgAge:Option[Float] = avgStudentAge
    //println("avgAge = " + avgAge)
    println('testScalarOptionQuery + " passed.")
  }

  def testLikeOperator = {
    val q =
      from(students)(s=>
        where(s.name like "G%")
        select(s.id)
        orderBy(s.name)
      )

    validateQuery('testLikeOperator, q, identity[Int], List(gaitan.id,georgi.id,gontran.id))
  }

  def testNotOperator = {
    val q =
      from(students)(s=>
        where(not(s.name like "G%"))
        select(s.id)
        orderBy(s.name desc)
      )

    validateQuery('testNotOperator, q, identity[Int], List(xiao.id, pratap.id))
  }

  def testDateTypeMapping = {

    val mandarinCourse =
      courses.where(c => c.id === mandarin.id).single

    assert(mandarinCourse.startDate == feb2010,
      'testDateTypeMapping + " failed, expected " + feb2010 + " got " + mandarinCourse.startDate)

    mandarinCourse.startDate = feb2011

    courses.update(mandarinCourse)

    val mandarinCourse2011 =
      courses.where(c => c.id === mandarin.id).single

    assert(mandarinCourse.startDate == feb2011,
      'testDateTypeMapping + " failed, expected " + feb2011 + " got " + mandarinCourse.startDate)

    println('testDateTypeMapping + " passed.")
  }

  def testDateOptionMapping = {

    var groupTh =
      courses.where(c => c.id === groupTheory.id).single

    assert(groupTh.finalExamDate == Some(may2009),
      'testDateOptionMapping + " failed, expected " + Some(may2009) + " got " + groupTh.finalExamDate)


    // test date update :
    groupTh.finalExamDate = Some(feb2011)

    courses.update(groupTh)

    groupTh =
      courses.where(c => c.id === groupTheory.id).single

    assert(groupTh.finalExamDate == Some(feb2011),
      'testDateOptionMapping + " failed, expected " + Some(feb2011) + " got " + groupTh.finalExamDate)


    // test date update to null :
    
    groupTh.finalExamDate = None

    courses.update(groupTh)

    groupTh =
      courses.where(c => c.id === groupTheory.id).single

    assert(groupTh.finalExamDate == None,
      'testDateOptionMapping + " failed, expected " + None + " got " + groupTh.finalExamDate)


    // test date update from None to Some :
    
    groupTh.finalExamDate = Some(may2009)

    courses.update(groupTh)

    groupTh =
      courses.where(c => c.id === groupTheory.id).single

    assert(groupTh.finalExamDate == Some(may2009),
      'testDateOptionMapping + " failed, expected " + Some(may2009) + " got " + groupTh.finalExamDate)
    
    println('testDateOptionMapping + " passed.")
  }

  def testDateComparisonInWhereClause = {

//    val feb2010 = dateFormat.parse("2010-02-01")
// ...
//    val groupTheory = courses.insert(new Course("Group Theory", jan2009, Some(may2009), 0, None, false))
//    val heatTransfer = courses.insert(new Course("Heat Transfer", feb2009, None, 3, Some(1234), false))
//    val counterpoint = courses.insert(new Course("Counterpoint", feb2010, None,0, None, true))
//    val mandarin = courses.insert(new Course("Mandarin 101", feb2010, None, 0, None, true))

    val jan2010 = dateFormat.parse("2010-01-01")
    val mar2010 = dateFormat.parse("2010-03-01")

    val mandarinAndCounterpointCourses =
      from(courses)(c=>
        where(c.startDate > jan2010 and c.startDate < mar2010)
        select(c)
        orderBy(c.startDate asc, c.id asc)
      ).toList

    val expected = List(counterpoint.id,  mandarin.id)
    val result = mandarinAndCounterpointCourses.map(c=>c.id)

    assert(expected == result,
      'testDateComparisonInWhereClause + " expected " + expected + " got " + result)

    println('testDateComparisonInWhereClause + " passed.")
  }

  def testDateOptionComparisonInWhereClause = {

//    val jan2009 = dateFormat.parse("2009-01-01")
//...
//    val groupTheory = courses.insert(new Course("Group Theory", jan2009, Some(may2009), 0, None, false))
//    val heatTransfer = courses.insert(new Course("Heat Transfer", feb2009, None, 3, Some(1234), false))
//    val counterpoint = courses.insert(new Course("Counterpoint", feb2010, None,0, None, true))
//    val mandarin = courses.insert(new Course("Mandarin 101", feb2010, None, 0, None, true))

    val jan2008 = dateFormat.parse("2008-01-01")

    //Session.currentSession.setLogger(s => println(s))

    val result1 =
      from(courses)(c=>
        where(c.finalExamDate >= jan2008 and c.finalExamDate.isNotNull)
        select(c)
        orderBy(c.finalExamDate, c.id asc)
      ).toList.map(c=>c.id)

    val result2 =
      from(courses)(c=>
        where(c.finalExamDate <= jan2009)
        select(c)
        orderBy(c.finalExamDate, c.id asc)
      ).toList.map(c=>c.id)

    val result3 =
      from(courses)(c=>
        where(c.finalExamDate >= feb2009)
        select(c)
        orderBy(c.finalExamDate, c.id asc)
      ).toList.map(c=>c.id)

    val expected = List(groupTheory.id)

    assert(expected == result1,
      'testDateOptionComparisonInWhereClause + " expected " + expected + " got " + result1)

    assert(Nil == result2,
      'testDateOptionComparisonInWhereClause + " expected " + expected + " got " + result2)

    assert(expected == result3,
      'testDateOptionComparisonInWhereClause + " expected " + expected + " got " + result3)

    println('testDateOptionComparisonInWhereClause + " passed.")
  }

  def testNVLFunction = {

//    val groupTheory = courses.insert(new Course("Group Theory", jan2009, Some(may2009), 0, None, false))
//    val heatTransfer = courses.insert(new Course("Heat Transfer", feb2009, None, 3, Some(1234), false))
//    val counterpoint = courses.insert(new Course("Counterpoint", feb2010, None,0, None, true))
//    val mandarin = courses.insert(new Course("Mandarin 101", feb2010, None, 0, None, true))

    //Session.currentSession.setLogger(s => println(s))

    val result =
      from(courses)(c=>
        where(nvl(c.meaninglessLongOption, 3) <> 1234 and nvl(c.meaninglessLongOption, 3) === 3)
        select(&(nvl(c.meaninglessLongOption, 5)))
      ).toList : List[Long]

    val expected = List(5,5,5)

    assert(expected == result,
      'testNVLFunction + " expected " + expected + " got " + result)

    println('testNVLFunction + " passed.")    
  }
  
  def testLongTypeMapping = {

    var ht = courses.where(c => c.id === heatTransfer.id).single

    assert(ht.meaninglessLong == 3, "expected 3, got " + ht.meaninglessLong)
    assert(ht.meaninglessLongOption == Some(1234), "expected Some(1234), got " + ht.meaninglessLongOption)

    ht.meaninglessLong = -3
    ht.meaninglessLongOption = None

    courses.update(ht)

    ht = courses.where(c => c.id === heatTransfer.id).single
    
    assert(ht.meaninglessLong == -3, "expected -3, got " + ht.meaninglessLong)
    assert(ht.meaninglessLongOption == None, "expected None, got " + ht.meaninglessLongOption)

    ht.meaninglessLongOption = Some(4321)

    courses.update(ht)

    ht = courses.where(c => c.id === heatTransfer.id).single

    assert(ht.meaninglessLongOption == Some(4321), "expected Some(4321), got " + ht.meaninglessLongOption)

    ht.meaninglessLongOption = Some(1234)

    courses.update(ht)
    
    assert(ht.meaninglessLongOption == Some(1234), "expected Some(1234), got " + ht.meaninglessLongOption)

    passed('testLongTypeMapping)
  }

  def testBooleanTypeMapping = {

    var ht = courses.where(c => c.id === heatTransfer.id).single

    assert(! ht.confirmed, "expected false, got " + ht.confirmed)

//    ht.confirmed = true
//    courses.update(ht)

    update(courses)(c =>
      where(c.id === heatTransfer.id)
      set(c.confirmed := true)
    )

    ht = courses.where(c => c.id === heatTransfer.id).single
    assert(ht.confirmed, "expected true, got " + ht.confirmed)

//    ht.confirmed = false
//    courses.update(ht)

    update(courses)(c =>
      where(c.id === heatTransfer.id)
      set(c.confirmed := false)
    )

    ht = courses.where(c => c.id === heatTransfer.id).single

    assert(! ht.confirmed, "expected false, got " + ht.confirmed)
    
    passed('testBooleanTypeMapping)
  }

  def testBooleanOptionMapping = {

    //println(students.where(s => s.id === gontran.id).dumpAst)

    var g = students.where(s => s.id === gontran.id).single

    assert(g.isMultilingual.get, "expected Some(true), got " + g.isMultilingual)

    g.isMultilingual = None
    students.update(g)
    g = students.where(s => s.id === gontran.id).single
    assert(g.isMultilingual == None, "expected None, got " + g.isMultilingual)

    g.isMultilingual = Some(false)
    students.update(g)
    g = students.where(s => s.id === gontran.id).single
    assert(! g.isMultilingual.get, "expected Some(false), got " + g.isMultilingual)

    g.isMultilingual = Some(true)
    students.update(g)
    g = students.where(s => s.id === gontran.id).single
    assert(g.isMultilingual.get, "expected Some(true), got " + g.isMultilingual)
    
    passed('testBooleanOptionMapping)
  }

  def testFloatType = {

    var t = professors.where(p => p.id === tournesol.id).single

    assert(t.yearlySalary == 80.0, "expected 80.0, got " + t.yearlySalary)
    assert(t.weight == Some(70.5), "expected Some(70.5), got " + t.weight)

    t.yearlySalary = 90.5F
    t.weight = Some(75.7F)
    professors.update(t)
    t = professors.where(p => p.id === tournesol.id).single
    assert(t.yearlySalary == 90.5, "expected 90.5, got " + t.yearlySalary)
    assert(t.weight == Some(75.7F), "expected Some(75.7), got " + t.weight)

    t.weight = None
    professors.update(t)
    t = professors.where(p => p.id === tournesol.id).single
    assert(t.weight == None, "expected None, got " + t.weight)

    t.yearlySalary = 80.0F
    t.weight = Some(70.5F)
    professors.update(t)
    t = professors.where(p => p.id === tournesol.id).single
    assert(t.yearlySalary == 80.0, "expected 80.0, got " + t.yearlySalary)
    assert(t.weight == Some(70.5), "expected Some(70.5), got " + t.weight)

    passed('testFloatType)
  }

  def testForUpdate = {

    var t = professors.where(p => p.id === tournesol.id).forUpdate.single

    assert(t.yearlySalary == 80.0, "expected 80.0, got " + t.yearlySalary)
    assert(t.weight == Some(70.5), "expected Some(70.5), got " + t.weight)

    passed('testForUpdate)
  }

  def exerciseTypeSystem1 = {

    val q =
      from(professors, courseAssigments, students, courses, courseSubscriptions, addresses)(
       (p, ca, s, c, cs, a) =>
        where(
         p.id === ca.professorId and
         ca.courseId === c.id and
         cs.studentId === s.id and
         cs.courseId === c.id and
         s.addressId === a.id
        )
        groupBy(
          s.isMultilingual : TypedExpressionNode[Option[Boolean]],
          p.yearlySalary : TypedExpressionNode[Float],
          p.weight :  TypedExpressionNode[Option[Float]],
          a.appNumberSuffix : TypedExpressionNode[Option[String]],
          c.finalExamDate : TypedExpressionNode[Option[Date]],
          a.appNumber : TypedExpressionNode[Option[Int]],
          c.meaninglessLongOption : TypedExpressionNode[Option[Long]],
          c.meaninglessLongOption / (s.addressId+1) : TypedExpressionNode[Option[Double]] // TODO: fix NOT A GROUP BY exception ....
        )
        compute(
          min(p.id) : TypedExpressionNode[Option[Long]],
          avg(ca.id) : TypedExpressionNode[Option[Float]],
          avg(c.meaninglessLongOption) : TypedExpressionNode[Option[Double]],
          max(c.finalExamDate) : TypedExpressionNode[Option[Date]],
          min(a.numberSuffix) : TypedExpressionNode[Option[String]],
          max(s.isMultilingual) : TypedExpressionNode[Option[Boolean]],
          min(c.startDate)  : TypedExpressionNode[Option[Date]]
        )
      )

    try {
       q.single : GroupWithMeasures[
       (Option[Boolean],
        Float,
        Option[Float],
        Option[String],
        Option[Date],
        Option[Int],
        Option[Long],
        Option[Double]),
       (Option[Long],
        Option[Float],
        Option[Double],
        Option[Date],
        Option[String],
        Option[Boolean],
        Option[Date])
      ]
      passed('exerciseTypeSystem1)
    }
    catch {
      case e:Exception => {
        println("statement failed : \n" + q.statement)
        throw e
      }
    }
  }

  def testPartialUpdate1 = {

    val initialHT = courses.where(c => c.id === heatTransfer.id).single

    val q =
      from(courses)(c =>
        select((c.id, c.meaninglessLong, c.meaninglessLongOption))
        orderBy(c.id)
      )

    val b4 = q.toList

    var nRows = courses.update(c =>
       where(c.id gt -1)
       set(c.meaninglessLong := 123L,
           c.meaninglessLongOption :=  c.meaninglessLongOption + 456L)
              // when meaninglessLongOption is null,the SQL addition will have a null result
    )

    val expectedAfter = List((1,123,None), (2,123,Some(1690)), (3,123,None), (4,123,None))
    val after = q.toList

    assert(nRows == 4)
    assert(expectedAfter == after, "expected " + expectedAfter + " got " + after)
    
    // alternative syntax :
    nRows =
      update(courses)(c =>
        where(c.id gt -1)
        set(c.meaninglessLong := 0L,
            c.meaninglessLongOption :=  c.meaninglessLongOption - 456L)
      )

    assert(nRows == 4)
    
    courses.forceUpdate(initialHT)

    val afterReset = q.toList

    assert(b4 == afterReset, "expected " + afterReset + " got " + b4)

    passed('testPartialUpdate1)
  }

  def testOptimisticCC1 = {    

    Session.currentSession.connection.commit // we commit to release all locks

    var ht = courses.where(c => c.id === heatTransfer.id).single
    
    transaction {
      var ht2 = courses.where(c => c.id === heatTransfer.id).single
      courses.update(ht2)
    }
    
    var ex: Option[StaleUpdateException] = None
    try {
      courses.update(ht)
    }
    catch {
      case e:StaleUpdateException => ex = Some(e)
    }
    
    ex.getOrElse(error("StaleUpdateException should have get thrown on concurrent update test."))

    val expectedVersionNumber = ht.occVersionNumberZ + 1

    val actualVersionNumber =
      from(courses)(c => where(c.id === heatTransfer.id) select(c)).single.occVersionNumberZ
            
    assertEquals(expectedVersionNumber, actualVersionNumber, "optimistic CC failed")

    passed('testOptimisticCC1)
  }

  def testBatchInserts1 = {

    addresses.insert(List(
      new Address("St-Dominique",14, None,None,None),
      new Address("St-Urbain",23, None,None,None),
      new Address("Sherbrooke",1123, None,Some(454),Some("B"))
    ))

    addresses.insert(List(
      new Address("Van Horne",14, None,None,None)
    ))

    val streetNames = List("Van Horne", "Sherbrooke", "St-Urbain", "St-Dominique")

    val q = addresses.where(a => a.streetName in streetNames)
    
    assertEquals(4, q.Count : Long, "batched update test failed")

    addresses.delete(q)

    assertEquals(0, q.Count : Long, "batched update test failed")

    passed('testBatchInserts1)
  }

  def testBatchUpdate1 = {

    addresses.insert(List(
      new Address("St-Dominique",14, None,None,None),
      new Address("St-Urbain",23, None,None,None),
      new Address("Sherbrooke",1123, None,Some(454),Some("B"))
    ))

    addresses.insert(List(
      new Address("Van Horne",14, None,None,None)
    ))

    val streetNames = List("Van Horne", "Sherbrooke", "St-Urbain", "St-Dominique")

    val q = addresses.where(a => a.streetName in streetNames)

    addresses.update(q.map(a =>{a.streetName += "Z"; a}))

    val updatedStreetNames = List("Van HorneZ", "SherbrookeZ", "St-UrbainZ", "St-DominiqueZ")

    val updatedQ = addresses.where(a => a.streetName in updatedStreetNames)

    assertEquals(4, updatedQ.Count : Long, "batched update test failed")

    addresses.delete(updatedQ)

    assertEquals(0, updatedQ.Count : Long, "batched update test failed")

    passed('testBatchUpdate1)
  }

  def testBigDecimal = {

    //loggerOn
    
    val pt = professors.where(_.yearlySalaryBD.between(75, 80))

    assertEquals(1, pt.Count : Long, 'testBigDecimal)

    assertEquals(tournesol.id, pt.single.id, 'testBigDecimal)


    val babaZula = professors.insert(new Professor("Baba Zula", 80.0F, Some(70.5F), 80.0F, Some(260.1234567F : BigDecimal)))

    update(professors)(p=>
      where(p.id === babaZula.id)
      set(p.weightInBD := Some(261.123456111 : BigDecimal))
    )

    val babaZula2 = professors.where(_.weightInBD === Some(261.123456111: BigDecimal))

    assertEquals(261.123456111, babaZula2.single.weightInBD.get, 'testBigDecimal)

    update(professors)(p=>
      where(p.id === babaZula.id)
      set(p.weightInBD := Some(261.1234561112 : BigDecimal))
    )

    val babaZula3 = professors.where(_.weightInBD === Some(261.1234561112: BigDecimal))

    assertEquals(1, babaZula3.Count : Long, 'testBigDecimal)
  }
}


class Issue14 extends Schema with QueryTester {

  import org.squeryl.PrimitiveTypeMode._

  override def columnNameFromPropertyName(n:String) =
    NamingConventionTransforms.camelCase2underScore(n)


  val professors = table[Professor]("issue14")

  def testIssue14 = {
    try {
      transaction {
        Session.currentSession.setLogger(println(_))
        val stmt = Session.currentSession.connection.createStatement
        stmt.execute("""create table issue14 (
    yearly_Salary real not null,
    weight_In_B_D decimal(20,16),
    id number primary key not null,
    last_Name varchar2(123) not null,
    yearly_Salary_B_D decimal(20,16) not null,
    weight real
  )
""")
        stmt.execute("create sequence s_issue14")
      }
      transaction {
        // The problem is that because schema.create wasn't called in this JVM instance, the schema doesn't know
        // that the id should be auto-increment until too late, so id=1 gets inserted.  Then the
        // next one knows about the sequence, so it gets nextval, which is 1, resulting in a uniqueness violation.
        val moriarty = new Professor("Moriarty", 10000000.001f, None, 100, None)
        moriarty.id = 1;
        professors.insert(moriarty)
        val xavier = new Professor("Xavier", 10000000.001f, None, 100, None)
        xavier.id = 1;
        professors.insert(xavier)
        for (prof <- from(professors)(p=>select(p))) {
          println(prof.lastName + " : " + prof.id)
        }
      }
    }
    finally {
      transaction {drop}
    }
  }
}