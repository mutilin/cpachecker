/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
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
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cmdline;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import org.eclipse.cdt.core.dom.IASTServiceProvider;
import org.eclipse.cdt.core.dom.ICodeReaderFactory;
import org.eclipse.cdt.core.dom.IASTServiceProvider.UnsupportedDialectException;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.InternalASTServiceProvider;
import org.eclipse.core.resources.IFile;

import cfa.CFABuilder;
import cfa.CFACheck;
import cfa.CFAMap;
import cfa.CFAReduction;
import cfa.CFASimplifier;
import cfa.CFATopologicalSort;
import cfa.CPASecondPassBuilder;
import cfa.DOTBuilder;
import cfa.DOTBuilderInterface;
import cfa.objectmodel.BlankEdge;
import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFAFunctionDefinitionNode;
import cfa.objectmodel.CFANode;
import cfa.objectmodel.c.GlobalDeclarationEdge;
import cmdline.stubs.StubConfiguration;
import cmdline.stubs.StubFile;

import common.Pair;
import compositeCPA.CompositeCPA;

import cpa.art.ARTCPA;
import cpa.common.CPAConfiguration;
import cpa.common.LogManager;
import cpa.common.MainCPAStatistics;
import cpa.common.ReachedElements;
import cpa.common.algorithm.Algorithm;
import cpa.common.algorithm.CBMCAlgorithm;
import cpa.common.algorithm.CEGARAlgorithm;
import cpa.common.algorithm.CPAAlgorithm;
import cpa.common.algorithm.InvariantCollectionAlgorithm;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.CPAStatistics;
import cpa.common.interfaces.CPAWithStatistics;
import cpa.common.interfaces.ConfigurableProgramAnalysis;
import cpa.common.interfaces.Precision;
import cpa.common.interfaces.CPAStatistics.Result;
import exceptions.CFAGenerationRuntimeException;
import exceptions.CPAException;

@SuppressWarnings("restriction")
public class CPAMain {
  
  public static CPAConfiguration cpaConfig;
  public static LogManager logManager;

  private static class ShutdownHook extends Thread {
    
    private final CPAStatistics mStats;
    private final ReachedElements mReached;
    
    // if still null when run() is executed, analysis has been interrupted by user
    private Result mResult = null;
    
    public ShutdownHook(CPAStatistics pStats, ReachedElements pReached) {
      mStats = pStats; 
      mReached = pReached;
    }
    
    public void setResult(Result pResult) {
      assert mResult == null;
      mResult = pResult;
    }
    
    @Override
    public void run() {
      if (mResult == null) {
        mResult = Result.UNKNOWN;
      }
       
      logManager.flush();
      System.out.flush();
      System.err.flush();
      mStats.printStatistics(new PrintWriter(System.out), mResult, mReached);
      
      if (mResult == Result.UNKNOWN) {
        System.out.println("\n" +
            "***********************************************************************\n" +
            "* WARNING: Analysis interrupted!! The statistics might be unreliable! *\n" +
            "***********************************************************************"
        );
      }
    }
  }

  public static class InvalidCmdlineArgumentException extends Exception {

    private static final long serialVersionUID = -6526968677815416436L;

    private InvalidCmdlineArgumentException(String msg) {
      super(msg);
    }
  }
  
  public static void main(String[] args) {
    // initialize various components
    try {
      cpaConfig = createConfiguration(args);
    } catch (InvalidCmdlineArgumentException e) {
      System.err.println("Could not parse command line arguments: " + e.getMessage());
      System.exit(1);
    } catch (IOException e) {
      System.err.println("Could not read config file " + e.getMessage());
      System.exit(1);
    }
    logManager = LogManager.getInstance();
    
    // get code file name
    String[] names = cpaConfig.getPropertiesArray("analysis.programNames");
    if (names == null) {
      logManager.log(Level.SEVERE, "No code file given!");
      System.exit(1);
    }
    
    if (names.length != 1) {
      logManager.log(Level.SEVERE, 
              "Support for multiple code files is currently not implemented!");
      System.exit(1);
    }
    
    File sourceFile = new File(names[0]);
    if (!sourceFile.exists()) {
      logManager.log(Level.SEVERE, "File", names[0], "does not exist!");
      System.exit(1);
    }
    
    if (!sourceFile.isFile()) {
      logManager.log(Level.SEVERE, "File", names[0], "is not a normal file!");
      System.exit(1);
    }
    
    if (!sourceFile.canRead()) {
      logManager.log(Level.SEVERE, "File", names[0], "is not readable!");
      System.exit(1);
    }

    // run analysis
    CPAchecker(new StubFile(names[0]));
    
    //ensure all logs are written to the outfile
    logManager.flush();
  }
  
  public static CPAConfiguration createConfiguration(String[] args)
          throws InvalidCmdlineArgumentException, IOException {
    // get the file name
    String fileName = getConfigFileName(args);
    
    CPAConfiguration config = new CPAConfiguration(fileName);
    
    // if there are some commandline arguments, process them
    if (args != null) {
      processArguments(args, config);                
    }
    //normalizeValues();
    return config;
  }

  /**
   * if -config is specified in arguments, loads this properties file,
   * otherwise loads the file from a default location. Default properties file is
   * $CPACheckerMain/default.properties
   * @param args commandline arguments
   */
  private static String getConfigFileName(String[] args) throws InvalidCmdlineArgumentException {
    Iterator<String> argsIt = Arrays.asList(args).iterator();

    while (argsIt.hasNext()) {
      if (argsIt.next().equals("-config")) {
        if (argsIt.hasNext()) {
          return argsIt.next();
        } else {
          throw new InvalidCmdlineArgumentException("-config argument missing!");
        }
      }
    }
    return null;
  }

  /**
   * Reads the arguments and process them. If a corresponding key is found, the property
   * is updated
   * @param args commandline arguments
   * @throws Exception if an option is set but no value for the option is found
   */
  private static void processArguments(String[] args, CPAConfiguration config)
          throws InvalidCmdlineArgumentException {
    List<String> ret = new ArrayList<String>();

    Iterator<String> argsIt = Arrays.asList(args).iterator();

    while (argsIt.hasNext()) {
      String arg = argsIt.next();
      if (   handleArgument1("-outputpath", "output.path", arg, argsIt, config)
          || handleArgument1("-logfile", "log.file", arg, argsIt, config)
          || handleArgument1("-cfafile", "cfa.file", arg, argsIt, config)
          || handleArgument1("-predlistpath", "predicates.path", arg, argsIt, config)
          || handleArgument1("-entryfunction", "analysis.entryFunction", arg, argsIt, config)
      ) { 
        // nothing left to do 

      } else if (arg.equals("-dfs")) {
        config.setProperty("analysis.traversal", "dfs");
      } else if (arg.equals("-bfs")) {
        config.setProperty("analysis.traversal", "bfs");
      } else if (arg.equals("-topsort")) {
        config.setProperty("analysis.traversal", "topsort");
      } else if (arg.equals("-nolog")) {
        config.setProperty("log.level", "off");
        config.setProperty("log.consoleLevel", "off");
      } else if (arg.equals("-setprop")) {
        if (argsIt.hasNext()) {
          String[] bits = argsIt.next().split("=");
          if (bits.length != 2) {
            throw new InvalidCmdlineArgumentException(
                "-setprop argument must be a key=value pair!");
          }
          config.setProperty(bits[0], bits[1]);
        } else {
          throw new InvalidCmdlineArgumentException("-setprop argument missing!");
        }
      } else if (arg.equals("-help")) {
        System.out.println("OPTIONS:");
        System.out.println(" -outputpath");
        System.out.println(" -logfile");
        System.out.println(" -cfafile");
        System.out.println(" -predlistpath");
        System.out.println(" -entryfunction");
        System.out.println(" -dfs");
        System.out.println(" -bfs");
        System.out.println(" -nolog");
        System.out.println(" -setprop");
        System.out.println(" -help");
        System.exit(0);
      } else if (arg.equals("-config")) {
        // this has been processed earlier, in loadFileName
        argsIt.next(); // ignore config file name argument
      } else {
        ret.add(arg);
      }
    }

    // arguments with non-specified options are considered as file names
    if (!ret.isEmpty()) {
      Iterator<String> it = ret.iterator();
      String programNames = it.next();
      while (it.hasNext()) {
        programNames = programNames + ", " + it.next();
      }
      config.setProperty("analysis.programNames", programNames);
    }
  }

  /**
   * Handle a command line argument with one value.
   */
  private static boolean handleArgument1(String arg, String option, String currentArg,
        Iterator<String> args, CPAConfiguration config)
        throws InvalidCmdlineArgumentException {
    if (currentArg.equals(arg)) {
      if (args.hasNext()) {
        config.setProperty(option, args.next());
      } else {
        throw new InvalidCmdlineArgumentException(currentArg + " argument missing!");
      }
      return true;
    } else {
      return false;
    }
  }

// TODO implement this when you get really bored
//  private void normalizeValues() {
//    for (Enumeration<?> keys = propertyNames(); keys.hasMoreElements();) {
//      String k = (String) keys.nextElement();
//      String v = getProperty(k);
//    
//      // trim heading and trailing blanks (at least Java 1.4.2 does not take care of trailing blanks)
//      String v0 = v;
//      v = v.trim();
//      if (!v.equals(v0)) {
//        put(k, v);
//      }
//    
//      if ("true".equalsIgnoreCase(v) || "t".equalsIgnoreCase(v)
//            || "yes".equalsIgnoreCase(v) || "y".equalsIgnoreCase(v)) {
//        put(k, "true");
//      } else if ("false".equalsIgnoreCase(v) || "f".equalsIgnoreCase(v)
//            || "no".equalsIgnoreCase(v) || "n".equalsIgnoreCase(v)) {
//        put(k, "false");
//      }
//    }
//  }
  
  public static void CPAchecker(IFile file) {
    logManager.log(Level.FINE, "Analysis Started");
    
    // parse code file
    IASTTranslationUnit ast = parse(file);

    MainCPAStatistics stats = new MainCPAStatistics();

    // start measuring time
    stats.startProgramTimer();

    // create CFA
    Pair<CFAMap, CFAFunctionDefinitionNode> cfa = createCFA(ast);
    CFAMap cfas = cfa.getFirst();
    CFAFunctionDefinitionNode mainFunction = cfa.getSecond();
    
    try {
      ConfigurableProgramAnalysis cpa = createCPA(mainFunction, stats);
      
      Algorithm algorithm = createAlgorithm(cfas, cpa);
      
      ReachedElements reached = createInitialReachedSet(cpa, mainFunction);
      
      runAlgorithm(algorithm, reached, stats);

    } catch (CPAException e) {
      logManager.logException(Level.SEVERE, e, null);
    }
    
    // statistics are displayed by shutdown hook
  }
  
  /**
   * Parse the content of a file into an AST with the Eclipse CDT parser.
   * If an error occurs, the program is halted.
   * 
   * @param fileName  The file to parse.
   * @return The AST.
   */
  public static IASTTranslationUnit parse(IFile file) {
    IASTServiceProvider p = new InternalASTServiceProvider();
    
    ICodeReaderFactory codeReaderFactory = null;
    try {
       codeReaderFactory = createCodeReaderFactory();
    } catch (ClassNotFoundException e) {
      logManager.logException(Level.SEVERE, e, "ClassNotFoundException:" +
          "Missing implementation of ICodeReaderFactory, check your CDT version!");
      System.exit(1);
    }
    
    IASTTranslationUnit ast = null;
    try {
      ast = p.getTranslationUnit(file, codeReaderFactory, new StubConfiguration());
    } catch (UnsupportedDialectException e) {
      logManager.logException(Level.SEVERE, e, "UnsupportedDialectException:" +
          "Unsupported dialect for parser, check parser.dialect option!");
      System.exit(1);
    }

    logManager.log(Level.FINE, "Parser Finished");

    return ast;
  }
  
  /**
   * Get the right StubCodeReaderFactory depending on the current CDT version.
   * @return The correct implementation of ICodeReaderFactory.
   * @throws ClassNotFoundException If no matching factory is found.
   */
  private static ICodeReaderFactory createCodeReaderFactory() throws ClassNotFoundException {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    
    String factoryClassName;
    // determine CDT version by trying to load the IMacroCollector class which
    // only exists in CDT 4
    try {
      classLoader.loadClass("org.eclipse.cdt.core.dom.IMacroCollector");
      
      // CDT 4.0
      factoryClassName = "cmdline.stubs.StubCodeReaderFactoryCDT4";
    } catch (ClassNotFoundException e) {
      // not CDT 4.0
      factoryClassName = "cmdline.stubs.StubCodeReaderFactory";
    }

    // try to load factory class and execute the static getInstance() method
    try {
      Class<?> factoryClass = classLoader.loadClass(factoryClassName);
      Object factoryObject = factoryClass.getMethod("getInstance", (Class<?>[]) null)
                                                                  .invoke(null);
      
      return (ICodeReaderFactory) factoryObject;
    } catch (Exception e) {
      // simply wrap all possible exceptions in a ClassNotFoundException
      // this will terminate the program
      throw new ClassNotFoundException("Exception while instantiating " + factoryClassName, e);
    }
  }
  
  /**
   * --Refactoring:
   * Initializes the CFA. This method is created based on the 
   * "extract method refactoring technique" to help simplify the createCFA method body.
   * @param builder
   * @param cfas
   * @return
   */
  private static CFAFunctionDefinitionNode initCFA(final CFABuilder builder, final CFAMap cfas)
  {
    final Collection<CFAFunctionDefinitionNode> cfasList = cfas.cfaMapIterator();
    String mainFunctionName = CPAMain.cpaConfig.getProperty("analysis.entryFunction", "main");
    
    CFAFunctionDefinitionNode mainFunction = cfas.getCFA(mainFunctionName);
    
    if (mainFunction == null) {
      logManager.log(Level.SEVERE, "Function", mainFunctionName, "not found!");
      System.exit(0);
    }
    
    // simplify CFA
    if (CPAMain.cpaConfig.getBooleanValue("cfa.simplify")) {
      // TODO Erkan Simplify each CFA
      CFASimplifier simplifier = new CFASimplifier();
      simplifier.simplify(mainFunction);
    }

    // Insert call and return edges and build the supergraph
    if (CPAMain.cpaConfig.getBooleanValue("analysis.interprocedural")) {
      logManager.log(Level.FINE, "Analysis is interprocedural, adding super edges");
      
      boolean noExtCalls = CPAMain.cpaConfig.getBooleanValue("analysis.noExternalCalls");
      CPASecondPassBuilder spbuilder = new CPASecondPassBuilder(cfas, noExtCalls);
      
      for (CFAFunctionDefinitionNode cfa : cfasList) {
        spbuilder.insertCallEdges(cfa.getFunctionName());
      }
    }
    
    if (CPAMain.cpaConfig.getBooleanValue("analysis.useGlobalVars")){
      // add global variables at the beginning of main
      
      List<IASTDeclaration> globalVars = builder.getGlobalDeclarations();
      insertGlobalDeclarations(mainFunction, globalVars);
    }
    
    return mainFunction;
  }
  
  
  private static Pair<CFAMap, CFAFunctionDefinitionNode> createCFA(IASTTranslationUnit ast) {

    // Build CFA
    final CFABuilder builder = new CFABuilder();
    try {
      ast.accept(builder);
    } catch (CFAGenerationRuntimeException e) {
      // only log message, not whole exception because this is a C problem,
      // not a CPAchecker problem
      logManager.log(Level.SEVERE, e.getMessage());
      System.exit(0);
    }
    final CFAMap cfas = builder.getCFAs();
    final Collection<CFAFunctionDefinitionNode> cfasList = cfas.cfaMapIterator();
    final int numFunctions = cfas.size();
    
    // annotate CFA nodes with topological information for later use
    for(CFAFunctionDefinitionNode cfa : cfasList){
      CFATopologicalSort topSort = new CFATopologicalSort();
      topSort.topologicalSort(cfa);
    }
    
    // --Refactoring:
    CFAFunctionDefinitionNode mainFunction = initCFA(builder, cfas);
    
    // --Refactoring: The following commented section does not affect the actual 
    //                execution of the code
    
    // check the CFA of each function
    // enable only while debugging/testing
//    if(CPAMain.cpaConfig.getBooleanValue("cfa.check")){
//      for(CFAFunctionDefinitionNode cfa : cfasList){
//        CFACheck.check(cfa);
//      }
//    }

    // --Refactoring: The following section was relocated to after the "initCFA" method 
    
    // remove irrelevant locations
    if (CPAMain.cpaConfig.getBooleanValue("cfa.removeIrrelevantForErrorLocations")) {
      CFAReduction coi =  new CFAReduction();
      coi.removeIrrelevantForErrorLocations(mainFunction);

      if (mainFunction.getNumLeavingEdges() == 0) {
        CPAMain.logManager.log(Level.INFO, "No error locations reachable from " + mainFunction.getFunctionName()
              + ", analysis not necessary.");
        System.exit(0);
      }
    }
    
    // check the super CFA starting at the main function
    // enable only while debugging/testing
    if(CPAMain.cpaConfig.getBooleanValue("cfa.check")){
      CFACheck.check(mainFunction);
    }

    // write CFA to file
    if (CPAMain.cpaConfig.getBooleanValue("cfa.export")) {
      DOTBuilderInterface dotBuilder = new DOTBuilder();
      
      String cfaFile = CPAMain.cpaConfig.getProperty("cfa.file", "cfa.dot");
      //if no filename is given, use default value
      String path = CPAMain.cpaConfig.getProperty("output.path") + cfaFile;
      try {
        dotBuilder.generateDOT(cfasList, mainFunction,
            new File(path).getPath());
      } catch (IOException e) {
        logManager.logException(Level.WARNING, e,
          "Could not write CFA to dot file, check configuration option cfa.file!");
        // continue with analysis
      }
    }
    
    logManager.log(Level.FINE, "DONE, CFA for", numFunctions, "functions created");

    return new Pair<CFAMap, CFAFunctionDefinitionNode>(cfas, mainFunction);
  }

  /**
   * Insert nodes for global declarations after first node of CFA.
   */
  private static void insertGlobalDeclarations(
      final CFAFunctionDefinitionNode cfa, List<IASTDeclaration> globalVars) {
    if (globalVars.isEmpty()) {
      return;
    }
    // create a series of GlobalDeclarationEdges, one for each declaration,
    // and add them as successors of the input node
    List<CFANode> decls = new LinkedList<CFANode>();
    CFANode cur = new CFANode(0);
    cur.setFunctionName(cfa.getFunctionName());
    decls.add(cur);

    for (IASTDeclaration d : globalVars) {
      assert(d instanceof IASTSimpleDeclaration);
      IASTSimpleDeclaration sd = (IASTSimpleDeclaration)d;
      // TODO refactor this
//      if (sd.getDeclarators().length == 1 &&
//          sd.getDeclarators()[0] instanceof IASTFunctionDeclarator) {
//        if (cpaConfig.getBooleanValue("analysis.useFunctionDeclarations")) {
//          // do nothing
//        }
//        else {
//          System.out.println(d.getRawSignature());
//          continue;
//        }
//      }
      GlobalDeclarationEdge e = new GlobalDeclarationEdge(
          d.getRawSignature(),
          sd.getDeclarators(),
          sd.getDeclSpecifier());
      CFANode n = new CFANode(0);
      n.setFunctionName(cur.getFunctionName());
      e.initialize(cur, n);
      decls.add(n);
      cur = n;
    }

    // now update the successors of cfa
    for (int i = 0; i < cfa.getNumLeavingEdges(); ++i) {
      CFAEdge e = cfa.getLeavingEdge(i);
      e.setPredecessor(cur);
    }
    if (cfa.getLeavingSummaryEdge() != null) {
      cfa.getLeavingSummaryEdge().setPredecessor(cur);
    }
    // and add a blank edge connecting the first node in decl with cfa
    BlankEdge be = new BlankEdge("INIT GLOBAL VARS");
    be.initialize(cfa, decls.get(0));

    return;
  }
  
  private static void runAlgorithm(final Algorithm algorithm,
          final ReachedElements reached,
          final MainCPAStatistics stats) throws CPAException {
     
    // this is for catching Ctrl+C and printing statistics even in that
    // case. It might be useful to understand what's going on when
    // the analysis takes a lot of time...
    ShutdownHook shutdownHook = new ShutdownHook(stats, reached);
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    logManager.log(Level.INFO, "Starting analysis...");
    stats.startAnalysisTimer();
    
    algorithm.run(reached, CPAMain.cpaConfig.getBooleanValue("analysis.stopAfterError"));
    
    stats.stopAnalysisTimer();
    logManager.log(Level.INFO, "Analysis finished.");

    Result result = Result.UNKNOWN;
    for (AbstractElement reachedElement : reached) {
      if (reachedElement.isError()) {
        result = Result.UNSAFE;
        break;
      }
    }
    if (result == Result.UNKNOWN) {
      result = Result.SAFE;
    }
    
    shutdownHook.setResult(result);
  }

  private static ConfigurableProgramAnalysis createCPA(
      final CFAFunctionDefinitionNode mainFunction, MainCPAStatistics stats) throws CPAException {
    logManager.log(Level.FINE, "Creating CPAs");
    
    ConfigurableProgramAnalysis cpa = CompositeCPA.getCompositeCPA(mainFunction);

    if (CPAMain.cpaConfig.getBooleanValue("analysis.useART")) {
      cpa = ARTCPA.getARTCPA(mainFunction, cpa);
    }
        
    if (cpa instanceof CPAWithStatistics) {
      ((CPAWithStatistics)cpa).collectStatistics(stats.getSubStatistics());
    }
    return cpa;
  }
  
  private static Algorithm createAlgorithm(final CFAMap cfas,
      final ConfigurableProgramAnalysis cpa) throws CPAException {
    logManager.log(Level.FINE, "Creating algorithms");

    Algorithm algorithm = new CPAAlgorithm(cpa);
    
    if (CPAMain.cpaConfig.getBooleanValue("analysis.useRefinement")) {
      algorithm = new CEGARAlgorithm(algorithm);
    }
    
    if (CPAMain.cpaConfig.getBooleanValue("analysis.useInvariantDump")) {
      algorithm = new InvariantCollectionAlgorithm(algorithm);
    }
    
    if (CPAMain.cpaConfig.getBooleanValue("analysis.useCBMC")) {
      algorithm = new CBMCAlgorithm(cfas, algorithm);
    }
    return algorithm;
  }


  private static ReachedElements createInitialReachedSet(
      final ConfigurableProgramAnalysis cpa,
      final CFAFunctionDefinitionNode mainFunction) {
    logManager.log(Level.FINE, "Creating initial reached set");
    
    AbstractElement initialElement = cpa.getInitialElement(mainFunction);
    Precision initialPrecision = cpa.getInitialPrecision(mainFunction);
    ReachedElements reached = null;
    try {
      reached = new ReachedElements(CPAMain.cpaConfig.getProperty("analysis.traversal"));
    } catch (IllegalArgumentException e) {
      logManager.logException(Level.SEVERE, e, "ERROR, unknown traversal option");
      System.exit(1);
    }
    reached.add(initialElement, initialPrecision);
    return reached;
  }
}
