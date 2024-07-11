// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.util.identifiers.GeneralGlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralLocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralStructureFieldIdentifier;

public class PrecisionParser {
  private CFA cfa;
  private final LogManager logger;

  PrecisionParser(CFA pCfa, LogManager l) {
    cfa = pCfa;
    logger = l;
  }

  public Map<CFANode, Map<GeneralIdentifier, DataType>> parse(Path file) {
    if (file == null) {
      return ImmutableMap.of();
    }
    Map<CFANode, Map<GeneralIdentifier, DataType>> localStatistics = new HashMap<>();
    Map<Integer, CFANode> idToNodeMap = new HashMap<>();
    cfa.nodes().forEach(n -> idToNodeMap.put(n.getNodeNumber(), n));

    try (BufferedReader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
      String line;
      Set<CFANode> nodes = new HashSet<>();
      Map<GeneralIdentifier, DataType> info = new TreeMap<>();
      Pattern nodePattern = Pattern.compile("N[0-9]*$");

      while ((line = reader.readLine()) != null) {
        if (line.startsWith("N")) {
          // N1 - it's node identifier
          if (!info.isEmpty()) {
            // put all previous information into the map
            for (CFANode node : nodes) {
              putIntoMap(localStatistics, node, info);
            }
            info = new TreeMap<>();
            nodes.clear();
          }
          // More than one node is possible
          List<String> nodeNums = Splitter.on(",").splitToList(line);
          for (String sNode : nodeNums) {
            // Get node number
            Matcher matcher = nodePattern.matcher(sNode);
            if (!matcher.matches()) {
              logger.log(Level.WARNING, "Unknown node ", sNode);
            } else {
              String nodeId = matcher.group().substring(1);
              CFANode node = idToNodeMap.get(Integer.parseInt(nodeId));
              nodes.add(node);
            }
          }
        } else if (!line.isEmpty()) {
          if (nodes.isEmpty()) {
            logger.log(
                Level.WARNING,
                "Cannot parse precision file %s, node id needs to appear first.",
                file);
            return ImmutableMap.of();
          }
          // it's information about local statistics
          List<String> localSet = Splitter.on(";").splitToList(line);

          if (shouldBeSkipped(localSet)) {
            continue;
          }
          GeneralIdentifier id = parseId(localSet);
          DataType type = DataType.valueOf(Ascii.toUpperCase(localSet.get(3)));
          info.put(id, type);
        }
      }
      // Lots of time faced with problems due to old precision
      // Remove it
      Files.deleteIfExists(file);
      for (CFANode node : nodes) {
        putIntoMap(localStatistics, node, info);
      }
      return ImmutableMap.copyOf(localStatistics);
    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e, "Cannot parse precision file");
      return ImmutableMap.of();
    }
  }

  private GeneralIdentifier parseId(List<String> splittedLine) {
    String type = splittedLine.get(0);
    String name = splittedLine.get(1);
    int deref = Integer.parseInt(splittedLine.get(2));

    if (type.equalsIgnoreCase("g")) {
      // Global variable
      return new GeneralGlobalVariableIdentifier(name, deref);
    } else if (type.equalsIgnoreCase("l")) {
      // Local identifier
      return new GeneralLocalVariableIdentifier(name, deref);
    } else if (type.equalsIgnoreCase("s") || type.equalsIgnoreCase("f")) {
      // Structure (field) identifier
      return new GeneralStructureFieldIdentifier(name, deref);
    } else {
      throw new UnsupportedOperationException(
          splittedLine + " can not be parsed, please, move all checks to shouldBeSkipped()");
    }
  }

  private boolean shouldBeSkipped(List<String> set) {
    // Return identifier, it's not interesting for us
    return set.get(0).equalsIgnoreCase("r") || set.get(0).equalsIgnoreCase("func");
  }

  private void putIntoMap(
      Map<CFANode, Map<GeneralIdentifier, DataType>> map,
      CFANode node,
      Map<GeneralIdentifier, DataType> info) {
    if (node != null && info != null) {
      if (map.containsKey(node)) {
        logger.log(Level.WARNING, "Node " + node + " is already in precision");
      } else {
        map.put(node, info);
      }
    }
  }
}
