/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.notbam;

import com.google.common.base.Predicates;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGToDotWriter;
import org.sosy_lab.cpachecker.util.predicates.simpleformulas.Predicate;

public class Utils {
  public static void writeArg(ARGState state, Path target) {
    writeArg(state, target, null);
  }

  public static ARGState findRoot(ARGState state) {
    ARGState current = state;
    while (!current.getParents().isEmpty()) {
      current = current.getParents().iterator().next();
    }

    return current;
  }

  public static void writeArg(ARGState state, Path target, ARGState highlightState) {
    try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
      ARGToDotWriter.write(w, findRoot(state), ARGState::getChildren, Predicates.alwaysTrue(),
          (s, e) -> s.equals(highlightState) || e.equals(highlightState));
    } catch (IOException e) {
      throw new RuntimeException("Failed to write ARG", e);
    }
  }
}
