/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 **/
package com.solidfire.jsvcgen.codegen

import com.solidfire.jsvcgen.model._

class CSharpCodeFormatter( options: CliConfig, serviceDefintion: ServiceDefinition ) {

  private val directTypeNames = options.typenameMapping.getOrElse(
                                                                   Map(
                                                                        "boolean" → "bool",
                                                                        "integer" → "long",
                                                                        "number" → "double",
                                                                        "string" → "string",
                                                                        "float" → "double",
                                                                        "object" → "Newtonsoft.Json.Linq.JObject",
                                                                        "uint64" → "UInt64"
                                                                      )
                                                                 )
  private val structTypes     = options.valueTypes.getOrElse( List( "bool", "long", "double" ) ).toSet

  // Get all the types that are just aliases for other types
  protected val typeAliases: Map[String, TypeUse] =
    (for (typ ← serviceDefintion.types;
          alias ← typ.alias
          ; if !directTypeNames.contains(typ.name) // Filter out any aliases that are direct types
    ) yield (typ.name, alias)).toMap

  def getTypeName( src: String ): String = {
    directTypeNames.get(src)
    .orElse(typeAliases.get( src ).map( getTypeName ))
    .getOrElse(Util.camelCase( src, firstUpper = true ))
  }

  def getTypeName( src: TypeDefinition ): String = getTypeName( src.name )

  def getTypeName( src: TypeUse ): String = src match {
    case TypeUse( name, false, false ) => getTypeName( name )
    case TypeUse( name, false, true ) => getTypeName( name ) +
      (if (structTypes.contains( getTypeName( name ) )) "?" else "")
    case TypeUse( name, true, false ) => s"List<${getTypeName( name )}>"
    case TypeUse( name, true, true ) => s"List<${getTypeName( name )}>" // Lists in .NET are nullable by default
  }

  def getResultType( src: Option[ReturnInfo] ): String = src match {
    case Some( info ) => "Task<" + getTypeName( info.returnType ) + ">"
    case None => "Task"
  }

  def getMethodName( src: String ): String = Util.camelCase( src, firstUpper = true )

  def getMethodName( src: Method ): String = getMethodName( src.name )

  def getPropertyName( src: String ): String = Util.camelCase( src, firstUpper = true )

  def getPropertyName( src: Member ): String = getPropertyName( src.name )

  def getPropertyName( src: Parameter ): String = getPropertyName( src.name )

  def getRequiredParams (params: List[Parameter]): List[Parameter] = {
    params.filterNot(p => p.optional)
  }

  def buildMethod(method: Method): String = {
    getRequestObjMethod(method) + getConvenienceMethod(method) + getOneRequiredParamMethod(method)
  }

  def getOneRequiredParamMethod( method: Method ) : String = {
    val req = getRequiredParams(method.params)
    if (req.size == 1){
      val param: Parameter = req.head
      s"""
         |${getSinceAttribute(method)}
         |${getDeprecatedAttribute(method)}
         |public async ${getResultType(method.returnInfo)} ${getMethodName(method)}(${getTypeName(param.parameterType)} ${getParamName(param)})
         |{
         |  var obj = new {${getParameterUseList(req)}};
         |
         |  ${getSendRequestWithObj(method)}
         |}
       """.stripMargin
    }
    else ""
  }

  def getConvenienceMethod( method: Method ) : String = {
    val req = getRequiredParams(method.params)
    if (req.isEmpty){
      s"""
         |${getSinceAttribute(method)}
         |${getDeprecatedAttribute(method)}
         |public async ${getResultType(method.returnInfo)} ${getMethodName(method)}()
         |{
         |  ${getSendRequest(method)}
         |}
       """.stripMargin
    }
    else ""
  }

  def getRequestObjMethod( method: Method ) : String = {
    if (method.params.size > 0){
      s"""
         |${getSinceAttribute(method)}
         |${getDeprecatedAttribute(method)}
         |public async ${getResultType(method.returnInfo)} ${getMethodName(method)}(${getMethodName(method)}Request obj)
         |{
         |  ${getSendRequestWithObj(method)}
         |}
       """.stripMargin
    }
    else ""
  }

  def getSinceAttribute(attribute: Attribute) : String = {
    if (attribute.since.isDefined) {
      s"[Since(${attribute.since.map(_.toString()).getOrElse("0.0")}f)]"
    } else ""
  }

  def getDeprecatedAttribute(attribute: Attribute) : String = {
    if (attribute.deprecated.isDefined) {
      s"[DeprecatedAfter(${attribute.deprecated.map(_.version).getOrElse(0.0)}f)]"
    }
    else ""
  }

  def getSendRequestWithObj( method: Method) : String = {
    if (method.returnInfo.isEmpty){
      "await base.SendRequest(\"" + method.name + "\", obj);"
    }
    else {
      "return await base.SendRequest<" + getTypeName(method.returnInfo.get.returnType) +">(\"" + method.name + "\", obj);"
    }
  }

  def getSendRequest( method: Method) : String = {
    if (method.returnInfo.isEmpty){
      "await base.SendRequest(\"" + method.name + "\");"
    }
    else {
      "return await base.SendRequest<" + getTypeName(method.returnInfo.get.returnType) +">(\"" + method.name + "\");"
    }
  }


  def getParamName( src: String ): String = Util.camelCase( src, firstUpper = false )

  def getParamName( src: Member ): String = getParamName( src.name )

  def getParamName( src: Parameter ): String = getParamName( src.name )

  def getParameterList( params: List[Parameter] ): String =
    Util
      .stringJoin( for (param ← params) yield getTypeName( param.parameterType ) + " " + getParamName( param ), ", " )

  def getParameterUseList( params: List[Parameter] ): String =
    Util.stringJoin( for (param ← params) yield "@" + param.name + " = " + getParamName( param ), ", " )

  def getCodeDocumentation( lines: List[String], linePrefix: String ): String = {
    val sb = new StringBuilder
    sb.append( linePrefix )
      .append( "/// <summary>\n" )
    for (line ← lines) {
      sb.append( linePrefix )
        .append( "/// " )
        .append( line )
        .append( '\n' )
    }
    sb.append( linePrefix )
      .append( "/// </summary>" )
      .result( )
  }

  def getCodeDocumentation( doc: Documentation, linePrefix: String ): String =
    getCodeDocumentation( doc.lines, linePrefix )

  def ordered( types: List[TypeDefinition] ): List[TypeDefinition] = {
    val (aliases, fulls) = types.partition( x => x.alias.isDefined )
    aliases ++ fulls
  }
}
