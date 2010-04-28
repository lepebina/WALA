/******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.js.ipa.callgraph;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.cast.ipa.callgraph.StandardFunctionTargetSelector;
import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.cast.js.loader.JavaScriptLoaderFactory;
import com.ibm.wala.cast.js.translator.JSAstTranslator;
import com.ibm.wala.cast.js.translator.JavaScriptTranslatorFactory;
import com.ibm.wala.cast.js.types.JavaScriptMethods;
import com.ibm.wala.cast.js.types.JavaScriptTypes;
import com.ibm.wala.cast.loader.AstMethod.DebuggingInformation;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cast.util.CAstPrinter;
import com.ibm.wala.cfg.AbstractCFG;
import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

public class Util extends com.ibm.wala.cast.ipa.callgraph.Util {

  private static JavaScriptTranslatorFactory translatorFactory;

  public static void setTranslatorFactory(JavaScriptTranslatorFactory translatorFactory) {
    Util.translatorFactory = translatorFactory;
  }

  public static JavaScriptTranslatorFactory getTranslatorFactory() {
    return translatorFactory;
  }
  
  public static AnalysisOptions makeOptions(AnalysisScope scope, IClassHierarchy cha, Iterable<Entrypoint> roots) {
    final AnalysisOptions options = new AnalysisOptions(scope, /* AstIRFactory.makeDefaultFactory(keepIRs), */ roots);

    com.ibm.wala.ipa.callgraph.impl.Util.addDefaultSelectors(options, cha);
    options.setSelector(new StandardFunctionTargetSelector(cha, options.getMethodTargetSelector()));

    options.setUseConstantSpecificKeys(true);

    options.setUseStacksForLexicalScoping(true);

    return options;
  }

  public static JavaScriptLoaderFactory makeLoaders() {
    return new JavaScriptLoaderFactory(translatorFactory);
  }

  public static IClassHierarchy makeHierarchy(AnalysisScope scope, ClassLoaderFactory loaders)
      throws ClassHierarchyException {
    return ClassHierarchy.make(scope, loaders, JavaScriptLoader.JS);
  }

  public static Iterable<Entrypoint> makeScriptRoots(IClassHierarchy cha) {
    return new JavaScriptEntryPoints(cha, cha.getLoader(JavaScriptTypes.jsLoader));
  }

  public static Collection getNodes(CallGraph CG, String funName) {
    boolean ctor = funName.startsWith("ctor:");
    boolean suffix = funName.startsWith("suffix:");
    if (ctor) {
      TypeReference TR = 
        TypeReference.findOrCreate(JavaScriptTypes.jsLoader, 
            TypeName.string2TypeName("L"+ funName.substring(5)));
      MethodReference MR = JavaScriptMethods.makeCtorReference(TR);
      return CG.getNodes(MR);
    } else if (suffix) {
      Set<CGNode> nodes = new HashSet<CGNode>();
      String tail = funName.substring(7);
      for(CGNode n : CG) {
        if (n.getMethod().getReference().getDeclaringClass().getName().toString().endsWith(tail)) {
          nodes.add(n);
        }
      }
      return nodes;
    } else {
       TypeReference TR = TypeReference.findOrCreate(JavaScriptTypes.jsLoader, 
           TypeName.string2TypeName("L"+funName));
       MethodReference MR = AstMethodReference.fnReference(TR);
       return CG.getNodes(MR);
    }
  }

  public static void loadAdditionalFile(IClassHierarchy cha, JavaScriptLoader cl, String fileName, URL f) throws IOException {
    SourceURLModule M = new SourceURLModule(f);
    TranslatorToCAst toCAst = getTranslatorFactory().make(new CAstImpl(), M, f, f.getFile());
    final Set<String> names = new HashSet<String>();
    JSAstTranslator toIR = new JSAstTranslator(cl) {
      protected void defineFunction(CAstEntity N, 
          WalkContext definingContext, 
          AbstractCFG cfg,
          SymbolTable symtab, 
          boolean hasCatchBlock, 
          TypeReference[][] caughtTypes,
          boolean hasMonitorOp,
          AstLexicalInformation LI,
          DebuggingInformation debugInfo)
      {
        String fnName = "L" + composeEntityName(definingContext, N);
        names.add(fnName);
        super.defineFunction(N, definingContext, cfg, symtab, hasCatchBlock, caughtTypes, hasMonitorOp, LI, debugInfo);   
      }
    };
    CAstEntity tree = toCAst.translateToCAst();
    CAstPrinter.printTo(tree, new PrintWriter(System.err));
    toIR.translate(tree, fileName);
    for(String name : names) {
      IClass fcls = cl.lookupClass(name, cha);
      cha.addClass(fcls);
    }
  }
}
