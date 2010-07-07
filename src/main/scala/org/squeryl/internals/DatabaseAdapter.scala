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
package org.squeryl.internals

import org.squeryl.dsl.ast._
import org.squeryl._
import org.squeryl.{Schema, Session, Table}
import java.sql._

trait DatabaseAdapter {

  class Zip[T](val element: T, val isLast: Boolean, val isFirst: Boolean)
  
  class ZipIterable[T](iterable: Iterable[T]) {
    val count = iterable.size
    def foreach[U](f: Zip[T] => U):Unit = {
      var c = 1  
      for(i <- iterable) {
        f(new Zip(i, c == count, c == 1))
        c += 1
      }
    }

    def zipi = this
  }

  implicit def zipIterable[T](i: Iterable[T]) = new ZipIterable(i)

  def writeQuery(qen: QueryExpressionElements, sw: StatementWriter):Unit = {

    sw.write("Select")

    if(qen.selectDistinct)
      sw.write(" distinct")
    
    sw.nextLine
    sw.writeIndented {
      sw.writeNodesWithSeparator(qen.selectList.filter(e => ! e.inhibited), ",", true)
    }
    sw.nextLine
    sw.write("From")
    sw.nextLine
    sw.writeIndented {
      qen.outerJoinExpressions match {
        case Nil =>
            for(z <- qen.tableExpressions.zipi) {
              z.element.write(sw)
              sw.write(" ")
              sw.write(z.element.alias)
              if(!z.isLast) {
                sw.write(",")
                sw.nextLine
              }
            }
            sw.pushPendingNextLine
        case joinExprs =>
          for(z <- qen.tableExpressions.zipi) {
            if(z.isFirst) {
              z.element.write(sw)
              sw.write(" ")
              sw.write(z.element.alias)
              sw.indent
              sw.nextLine
            } else {
              sw.write("inner join ")
              z.element.write(sw)
              sw.write(" as ")
              sw.write(z.element.alias)
              sw.nextLine
            }
          }
          for(oje <- joinExprs.zipi) {
            writeOuterJoin(oje.element, sw)
            if(oje.isLast)
              sw.unindent
            sw.pushPendingNextLine
          }
      }
    }
    
    if(qen.whereClause != None && qen.whereClause.get.children.filter(c => !c.inhibited) != Nil) {      
      sw.write("Where")
      sw.nextLine
      sw.writeIndented {
        qen.whereClause.get.write(sw)
      }
      sw.pushPendingNextLine
    }

    if(! qen.groupByClause.isEmpty) {      
      sw.write("Group By")
      sw.nextLine
      sw.writeIndented {
        sw.writeNodesWithSeparator(qen.groupByClause.filter(e => ! e.inhibited), ",", true)
      }
      sw.pushPendingNextLine
    }
    
    if(! qen.orderByClause.isEmpty && qen.parent == None) {
      sw.write("Order By")
      sw.nextLine
      sw.writeIndented {
        sw.writeNodesWithSeparator(qen.orderByClause.filter(e => ! e.inhibited), ",", true)
      }
      sw.pushPendingNextLine
    }

    if(qen.isForUpdate) {
      sw.write("for update")
      sw.pushPendingNextLine
    }

    writePaginatedQueryDeclaration(qen, sw)
  }

  def writePaginatedQueryDeclaration(qen: QueryExpressionElements, sw: StatementWriter):Unit = 
    qen.page.foreach(p => {
      sw.write("limit ")
      sw.write(p._2.toString)
      sw.write(" offset ")
      sw.write(p._1.toString)
    })

  
  def writeOuterJoin(oje: OuterJoinExpression, sw: StatementWriter) = {
    sw.write(oje.leftRightOrFull)
    sw.write(" outer join ")
    oje.queryableExpressionNode.write(sw)
    sw.write(" as ")
    sw.write(oje.queryableExpressionNode.alias)
    sw.write(" on ")
    oje.matchExpression.write(sw)
  }

  def intTypeDeclaration = "int"
  def stringTypeDeclaration = "varchar(255)"  
  def stringTypeDeclaration(length:Int) = "varchar("+length+")"
  def booleanTypeDeclaration = "boolean"
  def doubleTypeDeclaration = "double"
  def dateTypeDeclaration = "date"
  def longTypeDeclaration = "bigint"
  def floatTypeDeclaration = "real"
  def bigDecimalTypeDeclaration = "decimal"
  def bigDecimalTypeDeclaration(precision:Int, scale:Int) = "decimal(" + precision + "," + scale + ")"
  def timestampTypeDeclaration = "date"
  def binaryTypeDeclaration = "binary"
  
  private val _declarationHandler = new FieldTypeHandler[String] {

    def handleIntType = intTypeDeclaration
    def handleStringType  = stringTypeDeclaration
    def handleStringType(fmd: Option[FieldMetaData]) = stringTypeDeclaration(fmd.get.length)
    def handleBooleanType = booleanTypeDeclaration
    def handleDoubleType = doubleTypeDeclaration
    def handleDateType = dateTypeDeclaration
    def handleLongType = longTypeDeclaration
    def handleFloatType = floatTypeDeclaration
    def handleBigDecimalType(fmd: Option[FieldMetaData]) = bigDecimalTypeDeclaration(fmd.get.length, fmd.get.scale)
    def handleTimestampType = timestampTypeDeclaration
    def handleBinaryType = binaryTypeDeclaration
    def handleUnknownType(c: Class[_]) =
      error("don't know how to map field type " + c.getName)
  }
  
  def databaseTypeFor(fmd: FieldMetaData) =
    _declarationHandler.handleType(fmd.wrappedFieldType, Some(fmd))

  def writeColumnDeclaration(fmd: FieldMetaData, isPrimaryKey: Boolean, schema: Schema): String = {

    val dbTypeDeclaration = schema._columnTypeFor(fmd, this)

    var res = "  " + fmd.columnName + " " + dbTypeDeclaration

    if(isPrimaryKey)
      res += " primary key"

    if(!fmd.isOption)
      res += " not null"
    
    if(supportsAutoIncrementInColumnDeclaration && fmd.isAutoIncremented)
      res += " auto_increment"

    res
  }

  def supportsAutoIncrementInColumnDeclaration:Boolean = true

  def writeCreateTable[T](t: Table[T], sw: StatementWriter, schema: Schema) = {

    sw.write("create table ")
    sw.write(t.name);
    sw.write(" (\n");
    val pk = t.posoMetaData.primaryKey;    
    sw.writeIndented {
      sw.writeLinesWithSeparator(
        t.posoMetaData.fieldsMetaData.map(
          fmd => writeColumnDeclaration(fmd, pk != None && pk.get == fmd, schema)
        ),
        ","
      )
    }
    sw.write(")\n ")
  }

  def prepareStatement(c: Connection, sw: StatementWriter, session: Session): PreparedStatement =
    prepareStatement(c, sw, c.prepareStatement(sw.statement), session)
  
  def prepareStatement(c: Connection, sw: StatementWriter, s: PreparedStatement, session: Session): PreparedStatement = {    

    session._addStatement(s)

    var i = 1;
    for(p <- sw.params) {

      if(p.isInstanceOf[Option[_]]) {
        val o = p.asInstanceOf[Option[_]]
        if(o == None)
          s.setObject(i, null)
        else
          s.setObject(i, convertToJdbcValue(o.get.asInstanceOf[AnyRef]))
      }
      else
        s.setObject(i, convertToJdbcValue(p))
      i += 1
    }
    s
  }

  private def _exec[A](s: Session, sw: StatementWriter, block: ()=>A): A =
    try {
      if(s.isLoggingEnabled)
        s.log(sw.toString)      
      block()
    }
    catch {
      case e: SQLException =>
          throw new RuntimeException(
            "Exception while executing statement : "+ e.getMessage+
           "\nerrorCode: " +
            e.getErrorCode + ", sqlState: " + e.getSQLState + "\n" +
            sw.statement, e)
    }    

  def failureOfStatementRequiresRollback = false

  /**
   * Some methods like 'dropTable' issue their statement, and will silence the exception.
   * For example dropTable will silence when isTableDoesNotExistException(theExceptionThrown).
   * It must be used carefully, and an exception should not be silenced unless identified.
   */
  protected def execFailSafeExecute(sw: StatementWriter, silenceException: SQLException => Boolean): Unit = {
    val s = Session.currentSession
    val c = s.connection
    val stat = c.createStatement
    val sp =
      if(failureOfStatementRequiresRollback) Some(c.setSavepoint)
      else None

    try {
      if(s.isLoggingEnabled)
        s.log(sw.toString)
      stat.execute(sw.statement)
    }
    catch {
      case e: SQLException =>
        if(silenceException(e))
          sp.foreach(c.rollback(_))
        else
          throw new RuntimeException(
            "Exception while executing statement,\n" +
            "SQLState:" + e.getSQLState + ", ErrorCode:" + e.getErrorCode + "\n :" +
            sw.statement, e)
    }
    finally {
      sp.foreach(c.releaseSavepoint(_))
      Utils.close(stat)
    }
  }
  
  implicit def string2StatementWriter(s: String) = {
    val sw = new StatementWriter(this)
    sw.write(s)
    sw
  }

  protected def exec[A](s: Session, sw: StatementWriter)(block: =>A): A =
    _exec[A](s, sw, block _)

  def executeQuery(s: Session, sw: StatementWriter) = exec(s, sw) {
    val st = prepareStatement(s.connection, sw, s)
    (st.executeQuery, st)
  }

  def executeUpdate(s: Session, sw: StatementWriter):(Int,PreparedStatement) = exec(s, sw) {
    val st = prepareStatement(s.connection, sw, s)
    (st.executeUpdate, st)
  }

  def executeUpdateForInsert(s: Session, sw: StatementWriter, ps: PreparedStatement) = exec(s, sw) {
    val st = prepareStatement(s.connection, sw, ps, s)
    (st.executeUpdate, st)
  }

  def writeInsert[T](o: T, t: Table[T], sw: StatementWriter):Unit = {

    val o_ = o.asInstanceOf[AnyRef]    
    val f = t.posoMetaData.fieldsMetaData.filter(fmd => !fmd.isAutoIncremented)

    sw.write("insert into ");
    sw.write(t.name);
    sw.write(" (");
    sw.write(f.map(fmd => fmd.columnName).mkString(", "));
    sw.write(") values ");
    sw.write(
      f.map(fmd => writeValue(o_, fmd, sw)
    ).mkString("(",",",")"));
  }

  /**
   * Converts field instances so they can be fed, and understood by JDBC
   * will not do conversion from None/Some, so @arg r should be a java primitive type or
   * a CustomType
   */
  def convertToJdbcValue(r: AnyRef) : AnyRef = {
    var v = r
    if(v.isInstanceOf[Product1[_]])
       v = v.asInstanceOf[Product1[Any]]._1.asInstanceOf[AnyRef]
    if(v.isInstanceOf[java.util.Date] && ! v.isInstanceOf[java.sql.Date]  && ! v.isInstanceOf[Timestamp])
       v = new java.sql.Date(v.asInstanceOf[java.util.Date].getTime)
    if(v.isInstanceOf[scala.math.BigDecimal])
       v = v.asInstanceOf[scala.math.BigDecimal].bigDecimal
    v
  }

//  see comment in def convertFromBooleanForJdbc
//    if(v.isInstanceOf[java.lang.Boolean])
//      v = convertFromBooleanForJdbc(v)
  
  // TODO: move to StatementWriter (since it encapsulates the 'magic' of swapping values for '?' when needed)
  //and consider delaying the ? to 'value' decision until execution, in order to make StatementWriter loggable
  //with values at any time (via : a kind of prettyStatement method)
  protected def writeValue(o: AnyRef, fmd: FieldMetaData, sw: StatementWriter):String =
    if(sw.isForDisplay) {
      val v = fmd.get(o)
      if(v != null)
        v.toString
      else
        "null"
    }
    else {
      sw.addParam(convertToJdbcValue(fmd.get(o)))
      "?"
    }

//  protected def writeValue(sw: StatementWriter, v: AnyRef):String =
//    if(sw.isForDisplay) {
//      if(v != null)
//        v.toString
//      else
//        "null"
//    }
//    else {
//      sw.addParam(convertToJdbcValue(v))
//      "?"
//    }

  def postCreateTable(s: Session, t: Table[_]) = {}
  
  def postDropTable(t: Table[_]) = {}

  def writeConcatFunctionCall(fn: FunctionNode[_], sw: StatementWriter) = {
    sw.write(fn.name)
    sw.write("(")
    sw.writeNodesWithSeparator(fn.args, ",", false)
    sw.write(")")    
  }

  def isFullOuterJoinSupported = true

  def writeUpdate[T](o: T, t: Table[T], sw: StatementWriter, checkOCC: Boolean) = {

    val o_ = o.asInstanceOf[AnyRef]
    val pkMd = t.posoMetaData.primaryKey.get

    sw.write("update ", t.name, " set ")
    sw.nextLine
    sw.indent
    sw.writeLinesWithSeparator(
      t.posoMetaData.fieldsMetaData.
        filter(fmd=> fmd != pkMd).
          map(fmd => {
            if(fmd.isOptimisticCounter)
              fmd.columnName + " = " + fmd.columnName + " + 1 "
            else
              fmd.columnName + " = " + writeValue(o_, fmd, sw)
          }),
      ","
    )
    sw.unindent
    sw.write("where")
    sw.nextLine
    sw.indent
    sw.write(pkMd.columnName, " = ", writeValue(o_, pkMd, sw))

    if(checkOCC)
      t.posoMetaData.optimisticCounter.foreach(occ => {
         sw.write(" and ")
         sw.write(occ.columnName)
         sw.write(" = ")
         sw.write(writeValue(o_, occ, sw))
      })
  }

  def writeDelete[T](t: Table[T], whereClause: Option[ExpressionNode], sw: StatementWriter) = {

    sw.write("delete from ")
    sw.write(t.name)
    if(whereClause != None) {
      sw.nextLine
      sw.write("where")
      sw.nextLine
      sw.writeIndented {
        whereClause.get.write(sw)
      }
    }
  }

  /**
   * unused at the moment, since all jdbc drivers adhere to the standard that :
   *  1 == true, false otherwise. If a new driver would not adhere
   * to this, the call can be uncommented in method convertToJdbcValue
   */
  def convertFromBooleanForJdbc(b: Boolean): Boolean = b

  /**
   * unused for the same reason as def convertFromBooleanForJdbc (see comment)
   */
  def convertToBooleanForJdbc(rs: ResultSet, i:Int): Boolean = rs.getBoolean(i)


  def writeUpdate(t: Table[_], us: UpdateStatement, sw : StatementWriter) = {

    val colsToUpdate = us.columns.iterator

    sw.write("update ")
    sw.write(t.name)
    sw.write(" set")
    sw.indent
    sw.nextLine
    for(z <- us.values.zipi) {
      val col = colsToUpdate.next
      sw.write(col.columnName)
      sw.write(" = ")
      val v = z.element
      v.write(sw)
      if(!z.isLast) {
        sw.write(",")
        sw.nextLine
      }      
    }

    if(t.posoMetaData.isOptimistic) {
      sw.write(",")
      sw.nextLine      
      val occ = t.posoMetaData.optimisticCounter.get
      sw.write(occ.columnName)
      sw.write(" = ")
      sw.write(occ.columnName + " + 1")
    }
    
    sw.unindent

    if(us.whereClause != None) {
      sw.nextLine
      sw.write("Where")
      sw.nextLine
      val whereClauseClosure = us.whereClause.get
      sw.writeIndented {
        whereClauseClosure().write(sw)
      }
    }
  }

  def nvlToken = "coalesce"

  def writeNvlCall(left: ExpressionNode, right: ExpressionNode, sw: StatementWriter) = {
    sw.write(nvlToken)
    sw.write("(")
    left.write(sw)
    sw.write(",")
    right.write(sw)
    sw.write(")")
  }

  /**
   * Figures out from the SQLException (ex.: vendor specific error code) 
   * if it's cause is a NOT NULL constraint violation
   */
  def isNotNullConstraintViolation(e: SQLException): Boolean = false  

  def foreingKeyConstraintName(foreingKeyTable: Table[_], idWithinSchema: Int) =
    foreingKeyTable.name + "FK" + idWithinSchema

  def writeForeingKeyDeclaration(
    foreingKeyTable: Table[_], foreingKeyColumnName: String,
    primaryKeyTable: Table[_], primaryKeyColumnName: String,
    referentialAction1: Option[ReferentialAction],
    referentialAction2: Option[ReferentialAction],
    fkId: Int) = {
    
    val sb = new StringBuilder(256)

    sb.append("alter table ")
    sb.append(foreingKeyTable.name)
    sb.append(" add constraint ")
    sb.append(foreingKeyConstraintName(foreingKeyTable, fkId))
    sb.append(" foreign key (")
    sb.append(foreingKeyColumnName)
    sb.append(") references ")
    sb.append(primaryKeyTable.name)
    sb.append("(")
    sb.append(primaryKeyColumnName)
    sb.append(")")

    val f =  (ra:ReferentialAction) => {
      sb.append(" on ")
      sb.append(ra.event)
      sb.append(" ")
      sb.append(ra.action)
    }

    referentialAction1.foreach(f)
    referentialAction2.foreach(f)

    sb.toString
  }

  protected def currenSession =
    Session.currentSession

  def writeDropForeignKeyStatement(foreingKeyTable: Table[_], fkName: String) =
    "alter table " + foreingKeyTable.name + " drop constraint " + fkName

  def dropForeignKeyStatement(foreingKeyTable: Table[_], fkName: String, session: Session):Unit =
    execFailSafeExecute(writeDropForeignKeyStatement(foreingKeyTable, fkName), e => true)

  def isTableDoesNotExistException(e: SQLException): Boolean

  def supportsForeignKeyConstraints = true

  def writeDropTable(tableName: String) =
    "drop table " + tableName

  def dropTable(t: Table[_]) =
    execFailSafeExecute(writeDropTable(t.name), e=> isTableDoesNotExistException(e))

  def writeSelectElementAlias(se: SelectElement, sw: StatementWriter) =
    sw.write(se.alias)

  def writeUniquenessConstraint(t: Table[_], cols: Iterable[FieldMetaData]) = {
    //ALTER TABLE TEST ADD CONSTRAINT NAME_UNIQUE UNIQUE(NAME)
    val sb = new StringBuilder(256)
    
    sb.append("alter table ")
    sb.append(t.name)
    sb.append(" add constraint ")
    sb.append(t.name + "CPK")
    sb.append(" unique(")
    sb.append(cols.map(_.columnName).mkString(","))
    sb.append(")")
    sb.toString
  }
}
