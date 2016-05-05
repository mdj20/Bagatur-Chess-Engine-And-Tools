/*
 *  BagaturChess (UCI chess engine and tools)
 *  Copyright (C) 2005 Krasimir I. Topchiyski (k_topchiyski@yahoo.com)
 *  
 *  Open Source project location: http://sourceforge.net/projects/bagaturchess/develop
 *  SVN repository https://bagaturchess.svn.sourceforge.net/svnroot/bagaturchess
 *
 *  This file is part of BagaturChess program.
 * 
 *  BagaturChess is open software: you can redistribute it and/or modify
 *  it under the terms of the Eclipse Public License version 1.0 as published by
 *  the Eclipse Foundation.
 *
 *  BagaturChess is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Eclipse Public License for more details.
 *
 *  You should have received a copy of the Eclipse Public License version 1.0
 *  along with BagaturChess. If not, see <http://www.eclipse.org/legal/epl-v10.html/>.
 *
 */
package bagaturchess.search.impl.rootsearch.parallel;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import bagaturchess.bitboard.api.IBinarySemaphore;
import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.bitboard.impl.utils.BinarySemaphore_Dummy;
import bagaturchess.search.api.IEvaluator;
import bagaturchess.search.api.IFinishCallback;
import bagaturchess.search.api.IRootSearchConfig_SMP;
import bagaturchess.search.api.ISearchConfig_MTD;
import bagaturchess.search.api.internal.ISearch;
import bagaturchess.search.api.internal.ISearchInfo;
import bagaturchess.search.api.internal.ISearchMediator;
import bagaturchess.search.impl.alg.BetaGenerator;
import bagaturchess.search.impl.alg.BetaGeneratorFactory;
import bagaturchess.search.impl.alg.IBetaGenerator;
import bagaturchess.search.impl.alg.SearchImpl;
import bagaturchess.search.impl.env.SharedData;
import bagaturchess.search.impl.evalcache.EvalCache;
import bagaturchess.search.impl.pv.PVHistoryEntry;
import bagaturchess.search.impl.pv.PVManager;
import bagaturchess.search.impl.tpt.TPTEntry;
import bagaturchess.search.impl.utils.SearchUtils;


public class SearchManager {
	
	//private static int MTD_INITIAL_STEP = 25;
	
	private ReadWriteLock lock;
	
	private long hashkey;
	
	private volatile int maxIterations;
	private volatile int currentdepth;
	
	private IBetaGenerator betasGen;
	private volatile List<Integer> betas;
	
	private ISearchMediator mediator;
	private SharedData sharedData;
	private IFinishCallback finishCallback;
	
	private volatile ISearchInfo lastInfoLower;

	
	public SearchManager(ISearchMediator _mediator, IBitBoard _bitboardForSetup, SharedData _sharedData, long _hashkey,
			int _startIteration, int _maxIterations, IFinishCallback _finishCallback) {

		lock = new ReentrantReadWriteLock();
		
		sharedData = _sharedData;
		mediator = _mediator;
		hashkey = _hashkey;
		maxIterations = _maxIterations;
		
		currentdepth = _startIteration;//1;
		
		finishCallback = _finishCallback;
		
		//lower_bound = ISearch.MIN;
		//upper_bound = ISearch.MAX;
		//curIterationEval = ISearch.MIN;
		//prevIterationEval = ISearch.MIN;
		//nodes = 0;
		
		betas = new ArrayList<Integer>();
		
		initBetas(_bitboardForSetup);
	}
	
	
	/*public IFinishCallback getFinishCallback() {
		return finishCallback;
	}*/
	
	
	/*public void addNodes(long _nodes) {
		//nodes += _nodes;
	}*/
	
	public void writeLock() {
		//System.out.println("lock");
		lock.writeLock().lock();
	}
	
	public void writeUnlock() {
		//System.out.println("unlock");
		lock.writeLock().unlock();
	}
	
	private void initBetas(IBitBoard bitboardForTesting) {
		
		int initialVal = 0;
		
		/*
		if (sharedData.getTPT() != null) {
			sharedData.getTPT().lock();
			TPTEntry entry = sharedData.getTPT().get(hashkey);
			if (entry != null && entry.getBestMove_lower() != 0) {
				initialVal = entry.getLowerBound();
				//if (sharedData.getEngineConfiguration().getSearchConfig().isOther_UseTPTInRoot()) {
					//prevIterationEval = initialVal;
				//}
			}
			sharedData.getTPT().unlock();
		}
		*/
		
		int threadsCount = ((IRootSearchConfig_SMP)sharedData.getEngineConfiguration()).getThreadsCount();
		
		if (betasGen != null) {
			
			betasGen = BetaGeneratorFactory.create(betasGen.getLowerBound(), threadsCount, mediator.getTrustWindow_MTD_Step());
			
		} else {
			
			int root_colour = bitboardForTesting.getColourToMove();
			IEvaluator evaluator = sharedData.getEvaluatorFactory().create(
					bitboardForTesting,
					new EvalCache(100, true, new BinarySemaphore_Dummy()),
					sharedData.getEngineConfiguration().getEvalConfig());
			
			int staticRootEval = (int) evaluator.fullEval(0, ISearch.MIN, ISearch.MAX, root_colour);
			
			betasGen = BetaGeneratorFactory.create(staticRootEval, threadsCount, mediator.getTrustWindow_MTD_Step());
		}
		
		
		betas = betasGen.genBetas();
		//System.out.println("initBetas: " + betas);
		
		/*int count = 1;
		betas.add(initialVal);
		
		int cur_step = 1;
		while (true) {
			if (count >= EngineConfigFactory.getSingleton().getThreadsCount()) break;
			betas.add(initialVal + cur_step * MTD_INITIAL_STEP);
			count++;
			if (count >= EngineConfigFactory.getSingleton().getThreadsCount()) break;
			betas.add(initialVal - cur_step * MTD_INITIAL_STEP);
			count++;				
			cur_step++;
		}*/
		
		//System.out.println("initBetas=" + betas + "	initialVal=" + initialVal);
	}
	
	
	private void updateBetas() {
		betas = betasGen.genBetas();
		
		//System.out.println("UPDATE BETAS: " + betas);
		/*if (lower_bound >= upper_bound) {
			throw new IllegalStateException();
		}
		betas.clear();
		int main_frame = upper_bound - lower_bound;
		int frame = main_frame / (1 + EngineConfigFactory.getSingleton().getThreadsCount());
		if (frame == 0) {
			frame = 1;
		}
		for (int i=0; i<EngineConfigFactory.getSingleton().getThreadsCount(); i++) {
			betas.add(lower_bound + (i + 1) * frame);
		}*/
		
		//System.out.println("updateBetas=" + betas + "	upper_bound=" + upper_bound + "	lower_bound=" + lower_bound);
	}
	
	public int nextBeta() {
		//lock.writeLock().lock();
		
		//System.out.println("nextBeta: " + betas);
		
		if (betas.size() == 0) {
			
			//TODO: Consider
			//int beta_fix = betasGen.getLowerBound() + (betasGen.getUpperBound() - betasGen.getLowerBound()) / 2;
			
			mediator.dump("Search instability with distribution: " + this);
			
			/*mediator.dump("THREAD DUMP 1");
			dumpStacks();
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			mediator.dump("THREAD DUMP 2");
			dumpStacks();
			*/
			mediator.dump("Betagen obj: " + betasGen);
			updateBetas();
			mediator.dump("The new betas are:" + betas);
					
			//throw new IllegalStateException(toString());
		}
		
		int result = betas.remove(0);
		//System.out.println("nextBeta_res: " + result);
		
		/*Iterator<Integer> iter = betas.iterator();
		if (!iter.hasNext()) {
			throw new IllegalStateException(toString());
		}
		
		int result = iter.next();
		betas.remove(result);*/
		
		//lock.writeLock().unlock();
		
		return result;
	}


	private void dumpStacks() {
		Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
		for (Thread cur: stacks.keySet()) {
			mediator.dump("THREAD: " + cur.getName());
			StackTraceElement[] threadStacks = stacks.get(cur);
			for (int i=0;i<threadStacks.length; i++) {
				String line = threadStacks[i].toString();
				mediator.dump("	" + line);
			}
		}
	}
	
	public void increaseLowerBound(int eval, ISearchInfo info, IBitBoard bitboardForTesting) {
		
		boolean sentPV = false;
		
		if (eval >= betasGen.getLowerBound()) {
			
			sentPV = true;
			
			if (eval == betasGen.getLowerBound()) {
				betasGen.increaseLower(eval + mediator.getTrustWindow_MTD_Step());
				mediator.dump("Search stability fix in increaseLowerBound with distribution: " + this + ". Lower bound moved to " + betasGen.getLowerBound());
			} else {
				betasGen.increaseLower(eval);
			}
		}
		
		boolean isLast = isLast();
		
		if (isLast) {
			finishDepth(bitboardForTesting);
			initBetas(bitboardForTesting);
			if (currentdepth > maxIterations && finishCallback != null) {
				finishCallback.ready();
			}
		} else {
			updateBetas();
		}
		
		if (sentPV) {
			
			if (isLast) {
				info.setLowerBound(false);
				info.setUpperBound(false);
			}
			
			sharedData.getPVs().putPV(hashkey, new PVHistoryEntry(info.getPV(), info.getDepth(), info.getEval()));
			
			if (mediator != null) {
				
				mediator.changedMajor(info);
				
				try {
					testPV(info, bitboardForTesting);
				} catch (Exception e) {
					mediator.dump(e);
				}
			}
			
			lastInfoLower = info;
		}
	}
	
	public void decreaseUpperBound(int eval, ISearchInfo info, IBitBoard bitboardForTesting) {
		
		boolean sentPV = false;
		
		if (eval <= betasGen.getUpperBound()) {
			
			sentPV = true;
			
			if (eval == betasGen.getUpperBound()) {
				betasGen.decreaseUpper(eval - mediator.getTrustWindow_MTD_Step());
				mediator.dump("Search stability fix in decreaseUpperBound with distribution: " + this + ". Upper bound moved to " + betasGen.getUpperBound());
			} else {
				betasGen.decreaseUpper(eval);
			}
		}
		
		boolean isLast = isLast();
		
		if (isLast) {
			finishDepth(bitboardForTesting);
			initBetas(bitboardForTesting);
			if (currentdepth > maxIterations && finishCallback != null) {
				finishCallback.ready();
			}
		} else {
			updateBetas();
		}
		
		if (sentPV) {
			
			if (isLast) {
				
				if (lastInfoLower != null) {
					
					if (mediator != null) {
						
						lastInfoLower.setLowerBound(false);
						lastInfoLower.setUpperBound(false);
						
						mediator.changedMajor(lastInfoLower);
						
						try {
							testPV(info, bitboardForTesting);
						} catch (Exception e) {
							mediator.dump(e);
						}
					}
					
					lastInfoLower = null;
				}
				
			} else {
				
				if (!betasGen.hasLowerBound()) {
						
					//sharedData.getPVs().putPV(hashkey, new PVHistoryEntry(info.getPV(), info.getDepth(), info.getEval()));
					
					if (mediator != null) {
						
						mediator.changedMajor(info);
						
						try {
							testPV(info, bitboardForTesting);
						} catch (Exception e) {
							mediator.dump(e);
						}
					}
				}
			}
		}
	}
	
	
	private void testPV(ISearchInfo info, IBitBoard bitboardForTesting) {
		
		if (true) return;
		
		//if (!sharedData.getEngineConfiguration().verifyPVAfterSearch()) return;
		
		int root_colour = bitboardForTesting.getColourToMove();
		
		int sign = 1;
		
		int[] moves = info.getPV();
		
		for (int i=0; i<moves.length; i++) {
			bitboardForTesting.makeMoveForward(moves[i]);
			sign *= -1;
		}

		IEvaluator evaluator = sharedData.getEvaluatorFactory().create(
				bitboardForTesting,
				new EvalCache(100, true, new BinarySemaphore_Dummy()),
				sharedData.getEngineConfiguration().getEvalConfig());
		
		int curEval = (int) (sign * evaluator.fullEval(0, ISearch.MIN, ISearch.MAX, root_colour));
		
		if (curEval != info.getEval()) {
			mediator.dump("SearchManager.testPV FAILED > curEval=" + curEval + ",	eval=" + info.getEval());
		} else {
			mediator.dump("SearchManager.testPV OK > curEval=" + curEval + ",	eval=" + info.getEval());
		}
		
		for (int i=moves.length - 1; i >= 0; i--) {
			bitboardForTesting.makeMoveBackward(moves[i]);
		}
	}
	
	
	private void finishDepth(IBitBoard bitboardForTesting) {
		
		//System.out.println("FINISHING DEPTH " + maxdepth);
		
		currentdepth++;
		
		//if (curIterationLastInfo != null) {
		//	throw new IllegalStateException("SearchManager: finishDepth - curIterationLastInfo != null");
		//}
		
		/*if (curIterationEval <= prevIterationEval) {
			//Sent pv
			sharedData.getPVs().putPV(hashkey,
					new PVHistoryEntry(curIterationLastInfo.getPV(), curIterationLastInfo.getDepth(), curIterationLastInfo.getEval()));
			
			if (mediator != null) {
				//curIterationLastInfo.setSearchedNodes(nodes);
				mediator.changedMajor(curIterationLastInfo);
				
				try {
					testPV(curIterationLastInfo, bitboardForTesting);
				} catch (Exception e) {
					mediator.dump(e);
				}
			}
		}*/
		
		//prevIterationEval = curIterationEval;
		//curIterationEval = ISearch.MIN;
		//curIterationLastInfo = null;
		
		mediator.startIteration(currentdepth - 1);
	}
	
	public int getCurrentDepth() {
		return currentdepth;
	}

	public long getLowerBound() {
		return betasGen.getLowerBound();
	}
	
	public long getUpperBound() {
		return betasGen.getUpperBound();
	}
	
	private boolean isLast() {
		//boolean last = betasGen.getLowerBound() + mediator.getTrustWindow_BestMove() >= betasGen.getUpperBound();
		boolean last = betasGen.getLowerBound() + mediator.getTrustWindow_BestMove()
				+ (((IRootSearchConfig_SMP)sharedData.getEngineConfiguration()).getThreadsCount() - 1) >= betasGen.getUpperBound();
				
		if (!last) {
			if (betasGen.getLowerBound() >= ISearch.MAX_MAT_INTERVAL
					&& SearchUtils.isMateVal(betasGen.getLowerBound())
					&& (betasGen.getUpperBound() - betasGen.getLowerBound() < ISearch.MAX_MAT_INTERVAL)
				) {
				//Mate found
				last = true;
			}
			
			if (betasGen.getUpperBound() <= -ISearch.MAX_MAT_INTERVAL
					&& SearchUtils.isMateVal(betasGen.getUpperBound())
					&& (betasGen.getUpperBound() - betasGen.getLowerBound() < ISearch.MAX_MAT_INTERVAL)
				) {
				//Mate found
				last = true;
			}
		}
		
		//System.out.println("Last=" + last);
		
		return last;
	}
	
	public String toString() {
		String result = "";
		result += "DISTRIBUTION-> Depth:" + currentdepth + ", Bounds: ["
				+ betasGen.getLowerBound() + " <-> " + betasGen.getUpperBound()
				+ "], ThreadsCount:" + ((IRootSearchConfig_SMP)sharedData.getEngineConfiguration()).getThreadsCount() + ", BETAS: " + betas;
		return result;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public IBetaGenerator getBetasGen() {
		return betasGen;
	}
}
