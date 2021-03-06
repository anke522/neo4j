/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal

import org.neo4j.cypher._
import org.neo4j.cypher.internal.compatibility.LFUCache

/**
  * Preparses Cypher queries.
  *
  * The PreParser converts queries like
  *
  *   'CYPHER 3.3 planner=cost,runtime=slotted MATCH (n) RETURN n'
  *
  * into
  *
  * PreParsedQuery(
  *   planner: 'cost'
  *   runtime: 'slotted'
  *   version: '3.3'
  *   statement: 'MATCH (n) RETURN n'
  * )
  */
class PreParser(configuredVersion: CypherVersion,
                configuredPlanner: CypherPlanner,
                configuredRuntime: CypherRuntime,
                planCacheSize: Int) {

  private final val ILLEGAL_PLANNER_RUNTIME_COMBINATIONS: Set[(CypherPlanner, CypherRuntime)] = Set((CypherPlanner.rule, CypherRuntime.compiled), (CypherPlanner.rule, CypherRuntime.slotted))
  private final val ILLEGAL_PLANNER_VERSION_COMBINATIONS: Set[(CypherPlanner, CypherVersion)] = Set((CypherPlanner.rule, CypherVersion.v3_3), (CypherPlanner.rule, CypherVersion.v3_5))

  private val preParsedQueries = new LFUCache[String, PreParsedQuery](planCacheSize)

  @throws(classOf[SyntaxException])
  def preParseQuery(queryText: String, profile: Boolean = false): PreParsedQuery = {
    val preParsedQuery = preParsedQueries.computeIfAbsent(queryText, actuallyPreParse(queryText))
    if (profile) preParsedQuery.copy(executionMode = CypherExecutionMode.profile)
    else preParsedQuery
  }

  private def actuallyPreParse(queryText: String): PreParsedQuery = {
    val preParsedStatement = exceptionHandler.runSafely(CypherPreParser(queryText))

    val executionMode: PPOption[CypherExecutionMode] = new PPOption(CypherExecutionMode.default)
    val version: PPOption[CypherVersion] = new PPOption(configuredVersion)
    val planner: PPOption[CypherPlanner] = new PPOption(configuredPlanner)
    val runtime: PPOption[CypherRuntime] = new PPOption(configuredRuntime)
    val updateStrategy: PPOption[CypherUpdateStrategy] = new PPOption(CypherUpdateStrategy.default)
    var debugOptions: Set[String] = Set()

    def parseOptions(options: Seq[PreParserOption]): Unit =
      for (option <- options) {
        option match {
          case e: ExecutionModePreParserOption =>
            executionMode.selectOrThrow(CypherExecutionMode(e.name), "Can't specify multiple conflicting Cypher execution modes")
          case VersionOption(v) =>
            version.selectOrThrow(CypherVersion(v), "Can't specify multiple conflicting Cypher versions")
          case p: PlannerPreParserOption if p.name == GreedyPlannerOption.name =>
            throw new InvalidArgumentException("The greedy planner has been removed in Neo4j 3.1. Please use the cost planner instead.")
          case p: PlannerPreParserOption =>
            planner.selectOrThrow(CypherPlanner(p.name), "Can't specify multiple conflicting Cypher planners")
          case r: RuntimePreParserOption =>
            runtime.selectOrThrow(CypherRuntime(r.name), "Can't specify multiple conflicting Cypher runtimes")
          case u: UpdateStrategyOption =>
            updateStrategy.selectOrThrow( CypherUpdateStrategy(u.name), "Can't specify multiple conflicting update strategies")
          case DebugOption(debug) =>
            debugOptions = debugOptions + debug.toLowerCase()
          case ConfigurationOptions(versionOpt, innerOptions) =>
            for (v <- versionOpt)
              version.selectOrThrow(CypherVersion(v.version), "Can't specify multiple conflicting Cypher versions")
            parseOptions(innerOptions)
        }
      }

    parseOptions(preParsedStatement.options)

    if (ILLEGAL_PLANNER_RUNTIME_COMBINATIONS((planner.pick, runtime.pick)))
      throw new InvalidPreparserOption(s"Unsupported PLANNER - RUNTIME combination: ${planner.pick.name} - ${runtime.pick.name}")

    // Only disallow using rule if incompatible version is explicitly requested
    if (version.isSelected && ILLEGAL_PLANNER_VERSION_COMBINATIONS((planner.pick, version.pick)))
      throw new InvalidArgumentException(s"Unsupported PLANNER - VERSION combination: ${planner.pick.name} - ${version.pick.name}")

    val isPeriodicCommit = preParsedStatement.statement.trim.startsWith("USING PERIODIC COMMIT")

    PreParsedQuery(preParsedStatement.statement,
                   preParsedStatement.offset,
                   queryText,
                   isPeriodicCommit,
                   version.pick,
                   executionMode.pick,
                   planner.pick,
                   runtime.pick,
                   updateStrategy.pick,
                   debugOptions)
  }

  private class PPOption[T](val default: T) {
    var selected: Option[T] = None

    def pick: T = selected.getOrElse(default)
    def isSelected: Boolean = selected.nonEmpty
    def selectOrThrow(t: T, errorMsg: String): Unit =
      selected match {
        case Some(previous) => if (previous != t) throw new InvalidPreparserOption(errorMsg)
        case None => selected = Some(t)
      }
  }

  class InvalidPreparserOption(msg: String) extends InvalidArgumentException(msg)
}
