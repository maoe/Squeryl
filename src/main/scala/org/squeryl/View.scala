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
package org.squeryl

import dsl.QueryDsl
import internals._
import java.sql.ResultSet

/**
 * This class can be used for read only tables or (database) views
 * for an updatable view, or table use Table[T] 
 */
class View[T] private [squeryl](_name: String, private[squeryl] val classOfT: Class[T], schema: Schema) extends Queryable[T] {

  def this(n:String)(implicit manifestT: Manifest[T]) =
    this(n, manifestT.erasure.asInstanceOf[Class[T]], DummySchema)

  def name = schema.tableNameFromClassName(_name)

  private [squeryl] def findFieldMetaDataForProperty(name: String) = posoMetaData.findFieldMetaDataForProperty(name)

  val posoMetaData = new PosoMetaData(classOfT, schema)

  private [squeryl] def allFieldsMetaData: Iterable[FieldMetaData] = posoMetaData.fieldsMetaData

  private val _emptyArray = new Array[Object](0);

  protected val _setPersisted =
    if(classOf[PersistenceStatus].isAssignableFrom(classOfT))
      (t:T) => t.asInstanceOf[PersistenceStatus]._isPersisted = true
    else
      (t:T) => {}
  
  private [squeryl] def _createInstanceOfRowObject = {
    val c = posoMetaData.constructor
    c._1.newInstance(c._2 :_*).asInstanceOf[AnyRef];
  }
  
  private [squeryl] def give(resultSetMapper: ResultSetMapper, resultSet: ResultSet) : T  = {

    val o = _createInstanceOfRowObject
    
    resultSetMapper.map(o, resultSet);
    val t = o.asInstanceOf[T]
    _setPersisted(t)
    t
  }

  def lookup[K](k: K)(implicit ev: T <:< KeyedEntity[K], dsl: QueryDsl): Option[T] = {
    //TODO: find out why scalac won't let dsl be passed to another method
    import dsl._

    val q = from(this)(a => dsl.where {
      FieldReferenceLinker.createEqualityExpressionWithLastAccessedFieldReferenceAndConstant(a.id, k)
    } select(a))
    q.headOption

  }  
}
