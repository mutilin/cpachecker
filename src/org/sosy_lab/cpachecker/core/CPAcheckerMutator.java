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
package org.sosy_lab.cpachecker.core;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Joiner;
import com.google.common.base.VerifyException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFAMutator;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.SpecificationProperty;

// @Options(prefix = "cfa.mutations")
public class CPAcheckerMutator extends CPAchecker {

  //  @Option(
  //      secure = true,
  //      name = "expectedResult",
  //      description = "Correct result of analysis. TRUE or FALSE with violated properties
  // string.")
  //  private Result expectedResult = Result.NOT_YET_STARTED;
  //
  //  @Option(
  //      secure = true,
  //      name = "expectedViolatedProperties",
  //      description = "Expected violated properties string.")
  //  private String expectedViolatedProerties = "";
  //
  //  private CPAcheckerResult correctResult =
  //      new CPAcheckerResult(expectedResult, expectedViolatedProerties, null, null, null);

  private CPAcheckerResult originalResult = null;
  private Throwable originalThrowable = null;
  private final MainCPAStatistics firstStats;
  private CFAMutator cfaMutator = new CFAMutator(config, logger, shutdownNotifier);

  public CPAcheckerMutator(
      Configuration pConfiguration, LogManager pLogManager, ShutdownManager pShutdownManager)
      throws InvalidConfigurationException {
    super(pConfiguration, pLogManager, pShutdownManager);

    if (runCBMCasExternalTool) {
      throw new InvalidConfigurationException(
          "Cannot use option 'analysis.algorithm.CBMC' along with "
              + "'cfa.mutations', because CFA will not be constructed.");
    }

    firstStats = stats;
  }

  @Override
  public CPAcheckerResult run(
      List<String> programDenotation, Set<SpecificationProperty> properties) {
    checkArgument(!programDenotation.isEmpty());

    logger.logf(Level.INFO, "%s (%s) started", getVersion(config), getJavaInformation());

    CPAcheckerResult lastResult = null;
    CPAcheckerResult currentResult = null;
    Throwable currentThrowable = null;

    for (int mutationRound = 0; true; mutationRound++) {
      logger.logf(Level.INFO, "Mutation round %d", mutationRound);

      try {
        lastResult = currentResult;
        currentResult = null;
        currentThrowable = null;
        result = Result.NOT_YET_STARTED;
        if (mutationRound == 0) {
          stats.setCFACreator(cfaMutator);
        } else {
          stats = new MainCPAStatistics(config, logger, shutdownNotifier);
        }

        currentResult = prepareAndRun(programDenotation, properties);

        if (cfa == null) {
          break;
        }

      } catch (IOException e) {
        logger.logUserException(Level.SEVERE, e, "Could not read file");
        break;

      } catch (ParserException e) {
        logger.logUserException(Level.SEVERE, e, "Parsing failed");
        StringBuilder msg = new StringBuilder();
        msg.append("Please make sure that the code can be compiled by a compiler.\n");
        if (e.getLanguage() == Language.C) {
          msg.append(
              "If the code was not preprocessed, please use a C preprocessor\n"
                  + "or specify the -preprocess command-line argument.\n");
        }
        msg.append(
            "If the error still occurs, please send this error message\n"
                + "together with the input file to cpachecker-users@googlegroups.com.\n");
        logger.log(Level.INFO, msg);
        break;

      } catch (ClassNotFoundException e) {
        logger.logUserException(
            Level.SEVERE, e, "Could not read serialized CFA. Class is missing.");

      } catch (InvalidConfigurationException e) {
        logger.logUserException(Level.SEVERE, e, "Invalid configuration");
        break;

      } catch (InterruptedException e) {
        // CPAchecker must exit because it was asked to
        // we return normally instead of propagating the exception
        // so we can return the partial result we have so far
        logger.logUserException(Level.WARNING, e, "Analysis interrupted");
        break;

      } catch (CPAException e) {
        logger.logUserException(Level.SEVERE, e, null);
        currentThrowable = e;

      } catch (VerifyException | AssertionError e) {
        for (final StackTraceElement ste : e.getStackTrace()) {
          if (ste.getClassName().contains("CFACreator")) {
            throw e;
          }
        }
        logger.logUserException(Level.SEVERE, e, null);
        currentThrowable = e;
      }

      // if we catch an exception to investigate, restore result
      if (currentResult == null) {
        currentResult = new CPAcheckerResult(result, "", reached, cfa, stats);
      }
      compareResults(currentResult, currentThrowable);
    }

    // if loop ends because of an exception, currentResult is still null
    if (currentResult == null) {
      return new CPAcheckerResult(result, "", reached, cfa, totalStats(stats));
    }

    logger.log(Level.INFO, "Mutations ended.");
    logger.log(Level.INFO, "Verification result:", originalResult.getResultString());
    if (originalThrowable != null) {
      logger.logUserException(Level.INFO, originalThrowable, null);
    }

    // lastResult is not null because cfa can not be null at mutationRound==0
    @SuppressWarnings("null")
    MainCPAStatistics lastStats = (MainCPAStatistics) lastResult.getStatistics();
    CFA lastCFA = lastResult.getCfa();

    return originalResult.with(lastCFA, totalStats(lastStats));
  }

  private Statistics totalStats(MainCPAStatistics pStats) {
    // TODO
    pStats.getSubStatistics().clear();
    firstStats.getSubStatistics().clear();
    pStats.getSubStatistics().add(firstStats);
    cfaMutator.collectStatistics(pStats.getSubStatistics());
    return pStats;
  }

  @Override
  protected CFA parse(List<String> fileNames, MainCPAStatistics stats)
      throws InvalidConfigurationException, IOException, ParserException, InterruptedException {

    logger.logf(Level.INFO, "Parsing CFA from file(s) \"%s\"", Joiner.on(", ").join(fileNames));

    CFA cfa = cfaMutator.parseFileAndCreateCFA(fileNames);

    // TODO serialisedFile?
    // stats.setCFACreator(cfaMutator);
    stats.setCFA(cfa);
    return cfa;
  }

  @SuppressWarnings("null")
  private void compareResults(CPAcheckerResult currentResult, Throwable currentThrowable) {

    if (originalResult == null) {
      if (currentResult.getCfa() == null) {
        throw new IllegalStateException("CFA is null at initial aanlysis run.");
      }
      // init originals
      originalResult = currentResult;
      originalThrowable = currentThrowable;
      logger.logf(Level.INFO, "original result: %s", originalResult.getResultString());
      if (originalThrowable != null) {
        logger.logf(Level.INFO, "original exception: %s", originalThrowable);
      }
      return;
    }

    boolean differentResult =
        originalResult.getResult() != currentResult.getResult()
            || (originalResult.getResult() == Result.FALSE
                && !originalResult.getResultString().equals(currentResult.getResultString()));

    boolean ot = originalThrowable != null;
    boolean ct = currentThrowable != null;
    boolean differentThrowableClass =
        ot && ct && !originalThrowable.getClass().equals(currentThrowable.getClass());
    boolean differentThrowableMessages =
        ot && ct && !originalThrowable.getMessage().equals(currentThrowable.getMessage());


    if (differentResult || ot != ct || differentThrowableClass) {
      // result changed
      logger.log(Level.INFO, "Result has changed, mutation rollback.");
      if (!ot) {
        logger.logf(Level.INFO, "Expected result: %s", originalResult.getResultString());
      } else {
        logger.logf(Level.INFO, "Expected exception: %s", originalThrowable.getMessage());
      }
      if (!ct) {
        logger.logf(Level.INFO, "Got result: %s", currentResult.getResultString());
      } else {
        logger.logf(Level.INFO, "Got exception: %s", currentThrowable.getMessage());
      }
      cfaMutator.rollback();

    } else if (differentThrowableMessages) {
      // error message changed
      logger.logf(
          Level.WARNING,
          "The result is considered unchanged, but error message is different:\noriginal: %s\ncurrent:  %s",
          originalThrowable.getMessage(),
          currentThrowable.getMessage());

    } else {
      // result remained the same
      logger.log(Level.INFO, "Result has not changed.");
      logger.logf(Level.FINE, "Got %s", currentResult.getResultString());
      if (currentThrowable != null) {
        logger.logf(Level.FINE, "With %s", currentThrowable.getMessage());
      }
    }
  }
}
