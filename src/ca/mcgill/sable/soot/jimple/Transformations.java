/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Jimple, a 3-address code Java(TM) bytecode representation.        *
 * Copyright (C) 1997, 1998 Raja Vallee-Rai (kor@sable.mcgill.ca)    *
 * All rights reserved.                                              *
 *                                                                   *
 * Modifications by Etienne Gagnon (gagnon@sable.mcgill.ca) are      *
 * Copyright (C) 1998 Etienne Gagnon (gagnon@sable.mcgill.ca).  All  *
 * rights reserved.                                                  *
 *                                                                   *
 * Modifications by Patrick Lam (plam@sable.mcgill.ca) are           *
 * Copyright (C) 1999 Patrick Lam.  All rights reserved.             *
 *                                                                   *
 * This work was done as a project of the Sable Research Group,      *
 * School of Computer Science, McGill University, Canada             *
 * (http://www.sable.mcgill.ca/).  It is understood that any         *
 * modification not identified as such is not covered by the         *
 * preceding statement.                                              *
 *                                                                   *
 * This work is free software; you can redistribute it and/or        *
 * modify it under the terms of the GNU Library General Public       *
 * License as published by the Free Software Foundation; either      *
 * version 2 of the License, or (at your option) any later version.  *
 *                                                                   *
 * This work is distributed in the hope that it will be useful,      *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of    *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU *
 * Library General Public License for more details.                  *
 *                                                                   *
 * You should have received a copy of the GNU Library General Public *
 * License along with this library; if not, write to the             *
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,      *
 * Boston, MA  02111-1307, USA.                                      *
 *                                                                   *
 * Java is a trademark of Sun Microsystems, Inc.                     *
 *                                                                   *
 * To submit a bug report, send a comment, or get the latest news on *
 * this project and other Sable Research Group projects, please      *
 * visit the web site: http://www.sable.mcgill.ca/                   *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

/*
 Reference Version
 -----------------
 This is the latest official version on which this file is based.

 Change History
 --------------
 A) Notes:

 Please use the following template.  Most recent changes should
 appear at the top of the list.

 - Modified on [date (March 1, 1900)] by [name]. [(*) if appropriate]
   [description of modification].

 Any Modification flagged with "(*)" was done as a project of the
 Sable Research Group, School of Computer Science,
 McGill University, Canada (http://www.sable.mcgill.ca/).

 You should add your copyright, using the following template, at
 the top of this file, along with other copyrights.

 *                                                                   *
 * Modifications by [name] are                                       *
 * Copyright (C) [year(s)] [your name (or company)].  All rights     *
 * reserved.                                                         *
 *                                                                   *

 B) Changes:

 - Modified on March 5, 1999 by Raja Vallee-Rai (rvalleerai@sable.mcgill.ca) (*)
   Changed aggregate to be iterative.  No longer returns a value.
   
 - Modified on March 3, 1999 by Raja Vallee-Rai (rvalleerai@sable.mcgill.ca) (*)
   Fixed a bug with dead-code elimination concerning field/array references.  
   (they can throw null-pointer exceptions so they should not be eliminated) 
   Dead-code elimination is now done iteratively until nothing changes. (not sure why
   it wasn't done before) 
   
 - Modified on March 3, 1999 by Raja Vallee-Rai (rvalleerai@sable.mcgill.ca) (*)
   Improved the aggregator to move field accesses past some types of writes.
      
 - Modified on February 3, 1999 by Patrick Lam (plam@sable.mcgill.ca) (*)
   Added changes in support of the Grimp intermediate
   representation (with aggregated-expressions).

 - Modified on January 25, 1999 by Raja Vallee-Rai (rvalleerai@sable.mcgill.ca). (*)
   Made transformations class public.
    
 - Modified on January 20, 1999 by Raja Vallee-Rai (rvalleerai@sable.mcgill.ca). (*)
   Moved packLocals method to ChaitinAllocator.java
    
 - Modified on November 2, 1998 by Raja Vallee-Rai (kor@sable.mcgill.ca) (*)
   Repackaged all source files and performed extensive modifications.
   First initial release of Soot.

 - Modified on July 29,1998 by Etienne Gagnon (gagnon@sable.mcgill.ca). (*)
   Changed assignTypesToLocals. It now uses Etienne's type inference
   algorithm.
   Changed renameLocals. Gives a different name to address and error
   variables.

 - Modified on 23-Jun-1998 by Raja Vallee-Rai (kor@sable.mcgill.ca). (*)
   Changed Hashtable to HashMap.

 - Modified on 15-Jun-1998 by Raja Vallee-Rai (kor@sable.mcgill.ca). (*)
   First internal release (Version 0.1).
*/

package ca.mcgill.sable.soot.jimple;

import ca.mcgill.sable.soot.*;
import ca.mcgill.sable.util.*;

public class Transformations
{
    public static void assignTypesToLocals(JimpleBody listBody)
    {
        if(Main.isVerbose)
            System.out.println("[" + listBody.getMethod().getName() + "] assigning types to locals...");

        //Jimple.printStmtListBody(listBody, System.out, false);

        if(!Main.oldTyping)
        {
            TypeResolver.assignTypesToLocals(listBody);
            return;
        }

        StmtList stmtList = listBody.getStmtList();

        // Set all local types to unknown.
        {
            Iterator localIt = listBody.getLocals().iterator();

            while(localIt.hasNext())
                ((Local) localIt.next()).setType(UnknownType.v());
        }

        // Perform iterations on code, changing the types of the locals.
        {
            boolean hasChanged = true;
            SootClassManager cm = listBody.getMethod().getDeclaringClass().getManager();

            while(hasChanged)
            {
                hasChanged = false;

                Iterator stmtIt = stmtList.iterator();

                while(stmtIt.hasNext())
                {
                    Stmt s = (Stmt) stmtIt.next();

                    if(s instanceof DefinitionStmt)
                    {
                        DefinitionStmt def = (DefinitionStmt) s;

                        if(def.getLeftOp() instanceof Local)
                        {
                            Local local = (Local) def.getLeftOp();
                            Type previousType = local.getType();

                            Type newType = (Type.toMachineType(def.getRightOp().getType()))
                                .merge(previousType, cm);

                            if(!previousType.equals(newType))
                                 hasChanged = true;

                            local.setType(newType);
                        }
                    }
                }
            }
        }

        // Set all unknown locals to java.lang.Object
        {
            Iterator localIt = listBody.getLocals().iterator();

            while(localIt.hasNext())
            {
                Local local = (Local) localIt.next();

                if(local.getType().equals(UnknownType.v()))
                    local.setType(RefType.v("java.lang.Object"));
            }
        }
    }

    public static void splitLocals(JimpleBody listBody)
    {
        StmtList stmtList = listBody.getStmtList();

        if(Main.isVerbose)
            System.out.println("[" + listBody.getMethod().getName() + "] Splitting locals...");

        Map boxToSet = new HashMap(stmtList.size() * 2 + 1, 0.7f);

        if(Main.isProfilingOptimization)
                Main.splitPhase1Timer.start();

        // Go through the definitions, building the boxToSet 
        {
            List code = stmtList;

            if(Main.isProfilingOptimization)
                Main.graphTimer.start();

            CompleteStmtGraph graph = new CompleteStmtGraph(stmtList);

            if(Main.isProfilingOptimization)
                Main.graphTimer.end();

            LocalDefs localDefs;
            if(Main.usePackedDefs) 
            {
                if(Main.isProfilingOptimization)
                    Main.defsTimer.start();

                localDefs = new SimpleLocalDefs(graph);
                
                if(Main.isProfilingOptimization)
                    Main.defsTimer.end();
            }
            else {
                LiveLocals liveLocals;
            
                if(Main.isProfilingOptimization)
                    Main.liveTimer.start();
    
                if(Main.usePackedLive) 
                    liveLocals = new SimpleLiveLocals(graph);
                else
                    liveLocals = new SparseLiveLocals(graph);

                if(Main.isProfilingOptimization)
                    Main.liveTimer.end();

                if(Main.isProfilingOptimization)
                    Main.defsTimer.start();
        
                localDefs = new SparseLocalDefs(graph, liveLocals);
                
                if(Main.isProfilingOptimization)
                    Main.defsTimer.end();
            }            

            if(Main.isProfilingOptimization)
                Main.usesTimer.start();

            LocalUses localUses = new SimpleLocalUses(graph, localDefs);

            if(Main.isProfilingOptimization)
                Main.usesTimer.end();

            Iterator codeIt = stmtList.iterator();

            while(codeIt.hasNext())
            {
                Stmt s = (Stmt) codeIt.next();

                if(!(s instanceof DefinitionStmt))
                    continue;

                DefinitionStmt def = (DefinitionStmt) s;

                if(def.getLeftOp() instanceof Local && !boxToSet.containsKey(def.getLeftOpBox()))
                {
                    Set visitedBoxes = new ArraySet(); // set of uses
                    Set visitedDefs = new ArraySet();

                    LinkedList defsToVisit = new LinkedList();
                    LinkedList boxesToVisit = new LinkedList();

                    Map boxToStmt = new HashMap(stmtList.size() * 2 + 1, 0.7f);
                    Set equivClass = new ArraySet();

                    defsToVisit.add(def);

                    while(!boxesToVisit.isEmpty() || !defsToVisit.isEmpty())
                    {
                        if(!defsToVisit.isEmpty())
                        {
                            DefinitionStmt d = (DefinitionStmt) defsToVisit.removeFirst();

                            equivClass.add(d.getLeftOpBox());
                            boxToSet.put(d.getLeftOpBox(), equivClass);

                            visitedDefs.add(d);

                            List uses = localUses.getUsesOf(d);
                            Iterator useIt = uses.iterator();

                            while(useIt.hasNext())
                            {
                                StmtValueBoxPair use = (StmtValueBoxPair) useIt.next();

                                if(!visitedBoxes.contains(use.valueBox) &&
                                    !boxesToVisit.contains(use.valueBox))
                                {
                                    boxesToVisit.addLast(use.valueBox);
                                    boxToStmt.put(use.valueBox, use.stmt);
                                }
                            }
                        }
                        else {
                            ValueBox box = (ValueBox) boxesToVisit.removeFirst();

                            equivClass.add(box);
                            boxToSet.put(box, equivClass);
                            visitedBoxes.add(box);

                            List defs = localDefs.getDefsOfAt((Local) box.getValue(),
                                (Stmt) boxToStmt.get(box));
                            Iterator defIt = defs.iterator();

                            while(defIt.hasNext())
                            {
                                DefinitionStmt d = (DefinitionStmt) defIt.next();

                                if(!visitedDefs.contains(d) && !defsToVisit.contains(d))
                                    defsToVisit.addLast(d);
                            }
                        }
                    }
                }
            }
        }

        if(Main.isProfilingOptimization)
            Main.splitPhase1Timer.end();

        if(Main.isProfilingOptimization)
            Main.splitPhase2Timer.start();

        // Assign locals appropriately.
        {
            Map localToUseCount = new HashMap(listBody.getLocalCount() * 2 + 1, 0.7f);
            Set visitedSets = new HashSet();
            Iterator it = boxToSet.values().iterator();

            while(it.hasNext())
            {
                Set equivClass = (Set) it.next();

                if(visitedSets.contains(equivClass))
                    continue;
                else
                    visitedSets.add(equivClass);

                ValueBox rep = (ValueBox) equivClass.iterator().next();
                Local desiredLocal = (Local) rep.getValue();

                if(!localToUseCount.containsKey(desiredLocal))
                {
                    // claim this local for this set

                    localToUseCount.put(desiredLocal, new Integer(1));
                }
                else {
                    // generate a new local

                    int useCount = ((Integer) localToUseCount.get(desiredLocal)).intValue() + 1;
                    localToUseCount.put(desiredLocal, new Integer(useCount));

                    Local local = new Local(desiredLocal.getName() + "$" + useCount, desiredLocal.getType());

                    listBody.addLocal(local);

                    // Change all boxes to point to this new local
                    {
                        Iterator j = equivClass.iterator();

                        while(j.hasNext())
                        {
                            ValueBox box = (ValueBox) j.next();

                            box.setValue(local);
                        }
                    }
                }
            }
        }
        
        if(Main.isProfilingOptimization)
            Main.splitPhase2Timer.end();

    }

    public static void removeUnusedLocals(StmtBody listBody)
    {
        StmtList stmtList = listBody.getStmtList();
        Set unusedLocals = new HashSet();

        // Set all locals as unused
            unusedLocals.addAll(listBody.getLocals());

        // Traverse statements noting all the uses
        {
            Iterator stmtIt = stmtList.iterator();

            while(stmtIt.hasNext())
            {
                Stmt s = (Stmt) stmtIt.next();

                // Remove all locals in defBoxes from unusedLocals
                {
                    Iterator boxIt = s.getDefBoxes().iterator();

                    while(boxIt.hasNext())
                    {
                        Value value = ((ValueBox) boxIt.next()).getValue();

                        if(value instanceof Local && unusedLocals.contains(value))
                            unusedLocals.remove(value);
                    }
                }

                // Remove all locals in useBoxes from unusedLocals
                {
                    Iterator boxIt = s.getUseBoxes().iterator();

                    while(boxIt.hasNext())
                    {
                        Value value = ((ValueBox) boxIt.next()).getValue();

                        if(value instanceof Local && unusedLocals.contains(value))
                            unusedLocals.remove(value);
                    }
                }
            }

        }

        // Remove all locals in unusedLocals
        {
            Iterator it = unusedLocals.iterator();
            List locals = listBody.getLocals();
            
            while(it.hasNext())
            {
                Local local = (Local) it.next();

                locals.remove(local);
            }
        }
    }

    /**
        Cleans up the code of the method by performing copy/constant propagation and dead code elimination.
        
        Right now it must only be called on JimpleBody's (as opposed to GrimpBody's) because 
        it checks for the different forms on the rhs such as fieldref, etc to determine if a statement
        has a side effect.  (FieldRef can throw a null pointer exception)
        
        A better way to handle this would be to have a method which returns whether the statement
        has a side effect.
     */
     
    public static void cleanupCode(JimpleBody stmtBody)
    {
        StmtList stmtList = stmtBody.getStmtList();
        int numPropagations = 0;
        int numIterations = 0;
        int numEliminations = 0;
        
        for(;;)
        {
            boolean hadPropagation = false;
            boolean hadElimination = false;
            
            //JimpleBody.printStmtList_debug((StmtBody) stmtBody, new java.io.PrintWriter(System.out, true));
            
            numIterations++;

            if(Main.isVerbose)
                System.out.println("[" + stmtList.getBody().getMethod().getName() + "] Cleanup Iteration " + numIterations);

            //System.out.println("Before optimization:");
            //Jimple.printStmtListBody_debug(stmtList.getBody(), new java.io.PrintWriter(System.out, true));

            if(Main.isVerbose)
             System.out.println("[" + stmtList.getBody().getMethod().getName() + "] Constructing StmtGraph...");

            if(Main.isProfilingOptimization)
                Main.graphTimer.start();

            CompleteStmtGraph graph = new CompleteStmtGraph(stmtList);

            if(Main.isProfilingOptimization)
                Main.graphTimer.end();

            if(Main.isVerbose)
                System.out.println("[" + stmtList.getBody().getMethod().getName() + "] Constructing LocalDefs...");

            LocalDefs localDefs;
            
            if(Main.usePackedDefs) 
            {
                if(Main.isProfilingOptimization)
                    Main.defsTimer.start();

                localDefs = new SimpleLocalDefs(graph);
                
                if(Main.isProfilingOptimization)
                    Main.defsTimer.end();
            }
            else {
                LiveLocals liveLocals;
            
                if(Main.isProfilingOptimization)
                    Main.liveTimer.start();
                if(Main.usePackedLive) 
                    liveLocals = new SimpleLiveLocals(graph);
                else
                    liveLocals = new SparseLiveLocals(graph);    

                if(Main.isProfilingOptimization)
                    Main.liveTimer.end();

                if(Main.isProfilingOptimization)
                    Main.defsTimer.start();
        
                localDefs = new SparseLocalDefs(graph, liveLocals);
                
                if(Main.isProfilingOptimization)
                    Main.defsTimer.end();
            }           

            if(Main.isVerbose)
                System.out.println("[" + stmtList.getBody().getMethod().getName() + "] Constructing LocalUses...");

            if(Main.isProfilingOptimization)
                Main.usesTimer.start();

            LocalUses localUses = new SimpleLocalUses(graph, localDefs);

            if(Main.isProfilingOptimization)
                Main.usesTimer.end();

            if(Main.isVerbose)
                System.out.println("[" + stmtList.getBody().getMethod().getName() + "] Constructing LocalCopies...");

            if(Main.isProfilingOptimization)
                Main.copiesTimer.start();

            LocalCopies localCopies = new SimpleLocalCopies(graph);

            if(Main.isProfilingOptimization)
                Main.copiesTimer.end();

            if(Main.isProfilingOptimization)
                Main.cleanupAlgorithmTimer.start();

            // Perform a dead code elimination pass.
            {
                Iterator stmtIt = stmtList.iterator();

                while(stmtIt.hasNext())
                {
                    Stmt stmt = (Stmt) stmtIt.next();

                    if(stmt instanceof NopStmt)
                    {
                        stmtIt.remove();
                        continue;
                    }

                    if(!(stmt instanceof DefinitionStmt))
                        continue;

                    DefinitionStmt def = (DefinitionStmt) stmt;

                    if(def.getLeftOp() instanceof Local && localUses.getUsesOf(def).size() == 0 &&
                        !(def instanceof IdentityStmt))
                    {
                        // This definition is not used.
                        
                        Value rightOp = def.getRightOp();
                        
                        if(!(rightOp instanceof InvokeExpr) && 
                           !(rightOp instanceof InstanceFieldRef) &&
                           !(rightOp instanceof ArrayRef))
                        { 
                            // Does not have a side effect.

                            stmtIt.remove();
                            numEliminations++;
                            hadElimination = true;
                        }
                    }
                    else if(def.getLeftOp() instanceof Local && def.getRightOp() instanceof Local &&
                        def.getLeftOp() == def.getRightOp())
                    {
                        // This is an assignment of the form a = a, which is useless.

                        stmtIt.remove();
                        numEliminations++;
                        hadElimination = true;
                    }
                }
            }

            // Perform a constant/local propagation pass.
            {
                Iterator stmtIt = stmtList.iterator();

                while(stmtIt.hasNext())
                {
                    Stmt stmt = (Stmt) stmtIt.next();
                    Iterator useBoxIt = stmt.getUseBoxes().iterator();

                    while(useBoxIt.hasNext())
                    {
                        ValueBox useBox = (ValueBox) useBoxIt.next();

                        if(useBox.getValue() instanceof Local)
                        {
                            Local l = (Local) useBox.getValue();

                            List defsOfUse = localDefs.getDefsOfAt(l, stmt);

                            if(defsOfUse.size() == 1)
                            {
                                DefinitionStmt def = (DefinitionStmt) defsOfUse.get(0);

                                if(def.getRightOp() instanceof Constant)
                                {
                                    if(useBox.canContainValue(def.getRightOp()))
                                    {
                                        // Check to see if this box can actually contain
                                        // a constant.  (bases can't)

                                        useBox.setValue(def.getRightOp());
                                        numPropagations++;
                                        hadPropagation = true;
                                    }
                                }
                                else if(def.getRightOp() instanceof Local)
                                {
                                    Local m = (Local) def.getRightOp();

                                    if(localCopies.isLocalCopyOfBefore((Local) l, (Local) m,
                                        stmt))
                                    {
                                        useBox.setValue(m);
                                        numPropagations++;
                                        hadPropagation = true;
                                    }
                                }
                            }
                        }

                    }
                }
            }

            //JimpleBody.printStmtList_debug((StmtBody) stmtBody, new java.io.PrintWriter(System.out, true));

            if(!hadPropagation && !hadElimination)
                break;

            if(Main.isProfilingOptimization)
                Main.cleanupAlgorithmTimer.end();
        }

        //System.out.println("That's all folks:");
        //Jimple.printStmtListBody_debug(stmtList.getBody(), new java.io.PrintWriter(System.out, true));

    }

    public static void renameLocals(JimpleBody body)
    {
        StmtList stmtList = body.getStmtList();

        // Change the names to the standard forms now.
        {
            int objectCount = 0;
            int intCount = 0;
            int longCount = 0;
            int floatCount = 0;
            int doubleCount = 0;
            int addressCount = 0;
            int errorCount = 0;
            int nullCount = 0;

            Iterator localIt = body.getLocals().iterator();

            while(localIt.hasNext())
            {
                Local l = (Local) localIt.next();

                if(l.getType().equals(IntType.v()))
                    l.setName("i" + intCount++);
                else if(l.getType().equals(LongType.v()))
                    l.setName("l" + longCount++);
                else if(l.getType().equals(DoubleType.v()))
                    l.setName("d" + doubleCount++);
                else if(l.getType().equals(FloatType.v()))
                    l.setName("f" + floatCount++);
                else if(l.getType().equals(StmtAddressType.v()))
                    l.setName("a" + addressCount++);
                else if(l.getType().equals(ErroneousType.v()) ||
                    l.getType().equals(UnknownType.v()))
                {
                    l.setName("e" + errorCount++);
                }
                else if(l.getType().equals(NullType.v()))
                    l.setName("n" + nullCount++);
                else
                    l.setName("r" + objectCount++);
            }
        }
    }

  public static int nodeCount = 0;
  public static int aggrCount = 0;

  /** Look for a path, in g, from def to use. 
   * This path has to lie inside an extended basic block 
   * (and this property implies uniqueness.) */
  /* This path does not include the use */
  private static List getPath(StmtGraph g, Stmt def, Stmt use)
    {
      // if this holds, we're doomed to failure!!!
      if (g.getPredsOf(use).size() > 1)
        return null;

      // pathStack := list of succs lists
      // pathStackIndex := last visited index in pathStack
      LinkedList pathStack = new LinkedList();
      LinkedList pathStackIndex = new LinkedList();

      pathStack.add(def);
      pathStackIndex.add(new Integer(0));

      int psiMax = (g.getSuccsOf((Stmt)pathStack.get(0))).size();
      int level = 0;
      while (((Integer)pathStackIndex.get(0)).intValue() != psiMax)
        {
          int p = ((Integer)(pathStackIndex.get(level))).intValue();

          List succs = g.getSuccsOf((Stmt)(pathStack.get(level)));
          if (p >= succs.size())
            {
              // no more succs - backtrack to previous level.

              pathStack.remove(level);
              pathStackIndex.remove(level);

              level--;
              int q = ((Integer)pathStackIndex.get(level)).intValue();
              pathStackIndex.set(level, new Integer(q+1));
              continue;
            }

          Stmt betweenStmt = (Stmt)(succs.get(p));

          // we win!
          if (betweenStmt == use)
            {
              return pathStack;
            }

          // check preds of betweenStmt to see if we should visit its kids.
          if (g.getPredsOf(betweenStmt).size() > 1)
            {
              pathStackIndex.set(level, new Integer(p+1));
              continue;
            }

          // visit kids of betweenStmt.
          nodeCount++;
          level++;
          pathStackIndex.add(new Integer(0));
          pathStack.add(betweenStmt);
        }
      return null;
    }  


  /** Traverse the statements in the given body, looking for
   *  aggregation possibilities; that is, given a def d and a use u,
   *  d has no other uses, u has no other defs, collapse d and u. */
   
    public static void aggregate(StmtBody body)
    {
        int aggregateCount = 0;
        
        while(internalAggregate(body))
        {
            aggregateCount++;
        }
        
        if(Main.isVerbose)
            System.out.println(aggregateCount + " aggregation(s) performed.");
            
    }
  
  private static boolean internalAggregate(StmtBody body)
    {
      Iterator stmtIt;
      LocalUses localUses;
      LocalDefs localDefs;
      CompleteStmtGraph graph;
      boolean hadAggregation = false;
      StmtList stmtList = body.getStmtList();
      
      if(Main.isVerbose)
        System.out.println("[" + body.getMethod().getName() + "] Aggregating");

      if(Main.isVerbose)
        System.out.println("[" + body.getMethod().getName() + "] Constructing StmtGraph...");
      
      if(Main.isProfilingOptimization)
        Main.graphTimer.start();
          
      graph = new CompleteStmtGraph(stmtList);
      
      if(Main.isProfilingOptimization)
        Main.graphTimer.end();
          
      if(Main.isVerbose)
        System.out.println("[" + body.getMethod().getName() + "] Constructing LocalDefs...");
          
      if(Main.isProfilingOptimization)
        Main.defsTimer.start();
          
      localDefs = new SimpleLocalDefs(graph);

      if(Main.isProfilingOptimization)
        Main.defsTimer.end();
          
      if(Main.isVerbose)
        System.out.println("[" + body.getMethod().getName() + "] Constructing LocalUses...");
          
      if(Main.isProfilingOptimization)
        Main.usesTimer.start();
          
      localUses = new SimpleLocalUses(graph, localDefs);
          
      if(Main.isProfilingOptimization)
        Main.usesTimer.end();

      stmtIt = stmtList.iterator();
      
      
      while (stmtIt.hasNext())
        {
          Stmt s = (Stmt)(stmtIt.next());
              
          /* could this be definitionStmt instead? */
          if (!(s instanceof AssignStmt))
            continue;
          
          Value lhs = ((AssignStmt)s).getLeftOp();
          if (!(lhs instanceof Local))
            continue;
          
          List lu = localUses.getUsesOf((AssignStmt)s);
          if (lu.size() != 1)
            continue;
            
          StmtValueBoxPair usepair = (StmtValueBoxPair)lu.get(0);
          Stmt use = usepair.stmt;
              
          List ld = localDefs.getDefsOfAt((Local)lhs, use);
          if (ld.size() != 1)
            continue;
   
          /* we need to check the path between def and use */
          /* to see if there are any intervening re-defs of RHS */
          /* in fact, we should check that this path is unique. */
          /* if the RHS uses only locals, then we know what
             to do; if RHS has a method invocation f(a, b,
             c) or field access, we must ban field writes, other method
             calls and (as usual) writes to a, b, c. */
          
          boolean cantAggr = false;
      boolean propagatingInvokeExpr = false;
      boolean propagatingFieldRef = false;
          FieldRef fieldRef = null;
      
	  Value rhs = ((AssignStmt)s).getRightOp();
	  LinkedList localsUsed = new LinkedList();
	  for (Iterator useIt = (s.getUseBoxes()).iterator();
	       useIt.hasNext(); )
	    {
	      Value v = ((ValueBox)(useIt.next())).getValue();
	      if (v instanceof Local)
		localsUsed.add(v);
	        if (v instanceof InvokeExpr)
                propagatingInvokeExpr = true;
            else if(v instanceof FieldRef)
            {
                propagatingFieldRef = true;
                fieldRef = (FieldRef) v;
            }
	    }
	  
	  // look for a path from s to use in graph.
	  // only look in an extended basic block, though.

	  List path = getPath(graph, s, use);
	  if (path == null)
	    continue;

	  Iterator pathIt = path.iterator();

          // skip s.
          if (pathIt.hasNext())
            pathIt.next();

	  while (pathIt.hasNext() && !cantAggr)
	    {
	      Stmt between = (Stmt)(pathIt.next());
	      for (Iterator it = between.getDefBoxes().iterator();
		   it.hasNext(); )
		{
		  Value v = ((ValueBox)(it.next())).getValue();
		  if (localsUsed.contains(v))
		    { cantAggr = true; break; }
		  if (propagatingInvokeExpr || propagatingFieldRef)
		    {
		        if (v instanceof FieldRef)
			    {
                    if(propagatingInvokeExpr)
                    {
                        cantAggr = true; 
                        break;
                    }
                    else {
                        // Can't aggregate a field access if passing a definition of a field 
                        // with the same name, because they might be aliased
                        
                        if(((FieldRef) v).getField() == fieldRef.getField())
                        {
                            cantAggr = true;
                            break;
                        }
                    }
                     
                }
		    }
		}
	      
	      if(propagatingInvokeExpr || propagatingFieldRef)
		{
		  for (Iterator useIt = (s.getUseBoxes()).iterator();
		       useIt.hasNext(); )
		    {
		      Value v = ((ValueBox)(useIt.next())).getValue();
		      if (v instanceof InvokeExpr)
			cantAggr = true;
		    }
		}
	    }

	  // we give up: can't aggregate.
	  if (cantAggr)
          {
	    continue;
	  }
	  /* assuming that the d-u chains are correct, */
	  /* we need not check the actual contents of ld */
	  
	  Value aggregatee = ((AssignStmt)s).getRightOp();
          
	  if (usepair.valueBox.canContainValue(aggregatee))
	    {
	      usepair.valueBox.setValue(aggregatee);
	      body.eliminateBackPointersTo(s);
	      stmtIt.remove();
	      hadAggregation = true;
              aggrCount++;
	    }
	  else
	    {
            if(Main.isVerbose)
            {
	        System.out.println("[debug] failed aggregation");
	          System.out.println("[debug] tried to put "+aggregatee+
		    		 " into "+usepair.stmt + 
		    		 ": in particular, "+usepair.valueBox);
	          System.out.println("[debug] aggregatee instanceof Expr: "
		    		 +(aggregatee instanceof Expr));
            }
	    }
	}
      return hadAggregation;
    }
    
    public static void packLocals(StmtBody body)
    {
        Map localToGroup = new HashMap(body.getLocalCount() * 2 + 1, 0.7f);
        Map groupToColorCount = new HashMap(body.getLocalCount() * 2 + 1, 0.7f);
        Map localToColor = new HashMap(body.getLocalCount() * 2 + 1, 0.7f);
        Map localToNewLocal;
        
        // Assign each local to a group, and set that group's color count to 0.
        {
            Iterator localIt = body.getLocals().iterator();

            while(localIt.hasNext())
            {
                Local l = (Local) localIt.next();
                Object g = l.getType();
                
                localToGroup.put(l, g);
                
                if(!groupToColorCount.containsKey(g))
                {
                    groupToColorCount.put(g, new Integer(0));
                }
            }
        }

        // Assign colors to the parameter locals.
        {
            Iterator codeIt = body.getStmtList().iterator();

            while(codeIt.hasNext())
            {
                Stmt s = (Stmt) codeIt.next();

                if(s instanceof IdentityStmt &&
                    ((IdentityStmt) s).getLeftOp() instanceof Local)
                {
                    Local l = (Local) ((IdentityStmt) s).getLeftOp();
                    
                    Object group = localToGroup.get(l);
                    int count = ((Integer) groupToColorCount.get(group)).intValue();
                    
                    localToColor.put(l, new Integer(count));
                    
                    count++;
                    
                    groupToColorCount.put(group, new Integer(count));
                }
            }
        }
        
        // Call the graph colorer.
            FastColorer.assignColorsToLocals(body, localToGroup,
                localToColor, groupToColorCount);
                    
        // Map each local to a new local.
        {
            List originalLocals = new ArrayList();
            localToNewLocal = new HashMap(body.getLocalCount() * 2 + 1, 0.7f);
            Map groupIntToLocal = new HashMap(body.getLocalCount() * 2 + 1, 0.7f);
            
            originalLocals.addAll(body.getLocals());
            body.getLocals().clear();

            Iterator localIt = originalLocals.iterator();

            while(localIt.hasNext())
            {
                Local original = (Local) localIt.next();
                
                Object group = localToGroup.get(original);
                int color = ((Integer) localToColor.get(original)).intValue();
                GroupIntPair pair = new GroupIntPair(group, color);
                
                Local newLocal;
                
                if(groupIntToLocal.containsKey(pair))
                    newLocal = (Local) groupIntToLocal.get(pair);
                else {
                    newLocal = new Local(original.getName(), (Type) group);
                    groupIntToLocal.put(pair, newLocal);
                    body.getLocals().add(newLocal);
                }
                
                localToNewLocal.put(original, newLocal);
            }
        }

        
        // Go through all valueBoxes of this method and perform changes
        {
            Iterator codeIt = body.getStmtList().iterator();

            while(codeIt.hasNext())
            {
                Stmt s = (Stmt) codeIt.next();

                Iterator boxIt = s.getUseAndDefBoxes().iterator();

                while(boxIt.hasNext())
                {
                    ValueBox box = (ValueBox) boxIt.next();

                    if(box.getValue() instanceof Local)
                    {
                        Local l = (Local) box.getValue();
                        box.setValue((Local) localToNewLocal.get(l));
                    }
                }
            }
        }
    }
    
        
}
