/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cfa.mutation.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.util.Pair;

public class GlobalDeclarationStrategy
    extends GenericCFAMutationStrategy<Pair<ADeclaration, String>, Pair<Integer, Pair<ADeclaration, String>>> {

  public GlobalDeclarationStrategy(LogManager pLogger, int pAtATime, boolean ptryAllAtFirst) {
    super(pLogger, pAtATime, ptryAllAtFirst, "Global declarations");
  }

  @Override
  protected boolean canRemove(ParseResult pParseResult, Pair<ADeclaration, String> p) {
    return false;
  }

  @Override
  protected Collection<Pair<ADeclaration, String>> getAllObjects(ParseResult pParseResult) {
    List<Pair<ADeclaration, String>> answer = new ArrayList<>();
    for (Pair<ADeclaration, String> p : pParseResult.getGlobalDeclarations()) {
      if (canRemove(pParseResult, p)) {
        answer.add(0, p);
      }
    }
    return answer;
  }

  @Override
  protected Pair<Integer, Pair<ADeclaration, String>> getRollbackInfo(
      ParseResult pParseResult, Pair<ADeclaration, String> pObject) {
    return Pair.of(pParseResult.getGlobalDeclarations().indexOf(pObject), pObject);
  }

  @Override
  protected void removeObject(ParseResult pParseResult, Pair<ADeclaration, String> pObject) {
    List<Pair<ADeclaration, String>> prgd = pParseResult.getGlobalDeclarations();
    assert prgd.remove(pObject);
    assert !prgd.contains(pObject);
    pParseResult =
        new ParseResult(
            pParseResult.getFunctions(),
            pParseResult.getCFANodes(),
            prgd,
            pParseResult.getFileNames());
  }

  @Override
  protected void returnObject(
      ParseResult pParseResult, Pair<Integer, Pair<ADeclaration, String>> pRollbackInfo) {
    List<Pair<ADeclaration, String>> prgd = pParseResult.getGlobalDeclarations();
    assert !prgd.contains(pRollbackInfo.getSecond());
    prgd.add(pRollbackInfo.getFirst(), pRollbackInfo.getSecond());
    assert prgd.indexOf(pRollbackInfo.getSecond()) == pRollbackInfo.getFirst();
    pParseResult =
        new ParseResult(
            pParseResult.getFunctions(),
            pParseResult.getCFANodes(),
            prgd,
            pParseResult.getFileNames());
  }
}
