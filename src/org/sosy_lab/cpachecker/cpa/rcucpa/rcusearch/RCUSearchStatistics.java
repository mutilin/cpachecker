/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.rcucpa.rcusearch;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerState;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerStatistics;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@Options(prefix = "cpa.rcusearch")
public class RCUSearchStatistics implements Statistics {

  public static class RCUSearchStateStatistics {

    final StatTimer equalsTimer = new StatTimer("Overall time for equals check");
    final StatTimer equalsPointerTimer = new StatTimer("Time for pointer equals check");
    final StatTimer joinTimer = new StatTimer("Time for join states");
    final StatTimer joinPointerTimer = new StatTimer("Time for join pointer states");
    final StatTimer lessOrEqualsTimer = new StatTimer("Time for isLessOrEquals");
    final StatTimer lessOrEqualsPointerTimer =
        new StatTimer("Time for isLessOrEquals of pointer states");

    private final static RCUSearchStateStatistics instance = new RCUSearchStateStatistics();

    private RCUSearchStateStatistics() {
    }

    public void printStatistics(StatisticsWriter writer) {
      writer.beginLevel()
          .put(equalsTimer)
          .beginLevel()
          .put(equalsPointerTimer)
          .endLevel()
          .put(joinTimer)
          .beginLevel()
          .put(joinPointerTimer)
          .endLevel()
          .put(lessOrEqualsTimer)
          .beginLevel()
          .put(lessOrEqualsPointerTimer)
          .endLevel();
    }

    public static RCUSearchStateStatistics getInstance() {
      return instance;
    }
  }

  @Option(secure = true, name = "output", description = "name of a file to hold information about"
      + " RCU pointers and their aliases")
  @FileOption(Type.OUTPUT_FILE)
  private Path output = Paths.get("RCUPointers");

  private final LogManager logger;
  final StatTimer transferTimer = new StatTimer("Overall time for transfer relation");
  final StatTimer rcuSearchTimer = new StatTimer("Time for RCU search part");
  final StatTimer pointerTimer = new StatTimer("Time for pointer analysis");
  final StatTimer reducerTimer = new StatTimer("Overall time for reducer");
  final StatTimer rcuSearchReducerTimer = new StatTimer("Time for RCU search part");
  final StatTimer pointerReducerTimer = new StatTimer("Time for pointer analysis");

  RCUSearchStatistics(Configuration config, LogManager pLogger) throws
                                                                 InvalidConfigurationException {
    logger = pLogger;
    config.inject(this);
  }

  @Override
  @SuppressWarnings("serial")
  public void printStatistics(
      PrintStream out, Result result, UnmodifiableReachedSet reached) {

    Set<MemoryLocation> allRcuPointers = new TreeSet<>();
    Map<MemoryLocation, Set<MemoryLocation>> allPointsTo = new TreeMap<>();

    // TODO: BAM specifics?
    for (AbstractState state : reached) {
      RCUSearchState searchState = AbstractStates.extractStateByType(state, RCUSearchState.class);
      if (searchState != null) {
        allRcuPointers.addAll(searchState.getRcuPointers());
        Map<MemoryLocation, Set<MemoryLocation>> bufPT = searchState.getPointsTo();
        for (MemoryLocation key : bufPT.keySet()) {
          allPointsTo.putIfAbsent(key, new TreeSet<>());
          allPointsTo.get(key).addAll(bufPT.get(key));
        }
      }
    }

    Multimap<MemoryLocation, MemoryLocation> aliases = getAliases(allPointsTo);

    logger.log(Level.ALL, "RCU pointers in the last state: " + allRcuPointers);

    Set<MemoryLocation> rcuAndAliases = new TreeSet<>(allRcuPointers);

    for (MemoryLocation pointer : allRcuPointers) {
      if (!aliases.containsKey(pointer)) {
        logger.log(Level.WARNING, "No RCU pointer <" + pointer.toString() + "> in aliases");
      } else {
        Collection<MemoryLocation> buf = aliases.get(pointer);
        logger.log(Level.ALL, "Aliases for RCU pointer " + pointer + ": " + buf);
        rcuAndAliases.addAll(buf);
      }
    }
    if (output != null) {
      // May be disabled
      try (Writer writer = Files.newBufferedWriter(output, Charset.defaultCharset())) {
        Gson builder = new Gson();
        java.lang.reflect.Type type = new TypeToken<Set<MemoryLocation>>() {
        }.getType();
        builder.toJson(rcuAndAliases, type, writer);
        logger.log(Level.INFO, "Ended dump of RCU-aliases in file " + output);
      } catch (IOException pE) {
        logger.log(Level.WARNING, pE.getMessage());
      }
    }
    String info = "";
    info += "Number of RCU pointers:        " + allRcuPointers.size() + "\n";
    info += "Number of RCU aliases:         " + (rcuAndAliases.size() - allRcuPointers.size()) + "\n";
    info += "Number of fictional pointers:  " + getFictionalPointersNumber(rcuAndAliases) + "\n";
    out.append(info);
    logger.log(Level.ALL, "RCU with aliases: " + rcuAndAliases);
    StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(out);
    writer.beginLevel()
        .put(transferTimer)
        .beginLevel()
        .put(rcuSearchTimer)
        .put(pointerTimer)
        .endLevel()
        .put(reducerTimer)
        .beginLevel()
        .put(rcuSearchReducerTimer)
        .put(pointerReducerTimer)
        .endLevel()
        .endLevel()
        .spacer();
    RCUSearchStateStatistics.getInstance().printStatistics(writer);
  }

  @Nullable
  @Override
  public String getName() {
    return "RCU Search";
  }

  private Multimap<MemoryLocation, MemoryLocation> getAliases(Map<MemoryLocation,
                                                                     Set<MemoryLocation>> pointsTo) {
    Multimap<MemoryLocation, MemoryLocation> aliases = HashMultimap.create();
    for (MemoryLocation pointer : pointsTo.keySet()) {
      Set<MemoryLocation> pointerPointTo = pointsTo.get(pointer);
      if (pointerPointTo.contains(PointerStatistics.getReplLocSetTop())) {
        // pointer can point anywhere
        aliases.putAll(pointer, pointsTo.keySet());
        for (MemoryLocation other : pointsTo.keySet()) {
          // logger.log(Level.ALL, "Adding ", pointer, " to ", other, " as an alias");
          aliases.put(other, pointer);
        }
      } else if (!pointerPointTo.contains(PointerStatistics.getReplLocSetBot())) {
        Set<MemoryLocation> commonElems;
        for (MemoryLocation other : pointsTo.keySet()) {
          if (!other.equals(pointer)) {
            commonElems = new TreeSet<>(pointsTo.get(other));
            commonElems.retainAll(pointerPointTo);
            if (!commonElems.isEmpty()) {
              aliases.put(pointer, other);
              aliases.put(other, pointer);
            }
          }
        }
      }
    }
    return aliases;
  }

  private int getFictionalPointersNumber(Set<MemoryLocation> ptrs) {
    int result = 0;
    for (MemoryLocation iter : ptrs) {
      if (PointerState.isFictionalPointer(iter)) {
        ++result;
      }
    }
    return result;
  }
}
