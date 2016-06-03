package bagaturchess.search.impl.rootsearch.parallel;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bagaturchess.search.api.IRootSearch;
import bagaturchess.search.api.internal.ISearchInfo;
import bagaturchess.search.impl.info.SearchInfoFactory;
import bagaturchess.search.impl.utils.DEBUGSearch;
import bagaturchess.search.impl.utils.SearchUtils;
import bagaturchess.uci.api.ChannelManager;


public class SearchersInfo {

	
	private Map<IRootSearch, SearcherInfo> searchersInfo;
	private int cur_depth;
	private ISearchInfo last_send_info;
	
	
	public SearchersInfo(int startDepth) {
		searchersInfo = new HashMap<IRootSearch, SearcherInfo>();
		cur_depth = startDepth;
	}
	
	
	public int getCurrentDepth() {
		return cur_depth;
	}
	
	
	public void update(IRootSearch searcher, ISearchInfo info) {
		
		if (DEBUGSearch.DEBUG_MODE) ChannelManager.getChannel().dump("SearchersInfo: update info=" + info + ", info.getDepth()=" + info.getDepth() + ", info.getPV().length=" + info.getPV().length);
		
		SearcherInfo searcherinfo = searchersInfo.get(searcher);
		if (searcherinfo == null) {
			searcherinfo = new SearcherInfo();
			searchersInfo.put(searcher, searcherinfo);
		}
		
		searcherinfo.update(info);
	}
	
	
	//Result can be null
	public ISearchInfo getDeepestBestInfo() {
		
		ISearchInfo deepest_last_info = null;
		for (IRootSearch cur_searcher: searchersInfo.keySet()) {
			
			SearcherInfo cur_searcher_infos = searchersInfo.get(cur_searcher);
			ISearchInfo cur_deepest_last_info = cur_searcher_infos.getLastSearchInfo(cur_searcher_infos.getMaxDepth());
			
			if (cur_deepest_last_info != null) {
				if (deepest_last_info == null) {
					deepest_last_info = cur_deepest_last_info;
				} else {
					if (cur_deepest_last_info.getDepth() > deepest_last_info.getDepth()) {
						deepest_last_info = cur_deepest_last_info;
					} else if (cur_deepest_last_info.getDepth() == deepest_last_info.getDepth()) {
						if (cur_deepest_last_info.getEval() > deepest_last_info.getEval()) {
							deepest_last_info = cur_deepest_last_info;
						}
					}
				}
			}
		}
		
		return deepest_last_info;
	}
	
	
	public ISearchInfo getNewInfoToSendIfPresented() {
		
		if (last_send_info != null && last_send_info.getDepth() == cur_depth) {
			if (hasDepthInfo(cur_depth + 1)) {
				cur_depth++;
			}
		}
		
		ISearchInfo cur_depth_info = getInfoToSend(cur_depth);
		
		if (cur_depth_info != null) {
			if (last_send_info == null) {
				last_send_info = cur_depth_info;
				return cur_depth_info;
			} else {
				if (cur_depth_info.getDepth() != last_send_info.getDepth()
						|| cur_depth_info.getBestMove() != last_send_info.getBestMove()
						|| cur_depth_info.getEval() != last_send_info.getEval()
						) {
					last_send_info = cur_depth_info;
					return cur_depth_info;
				}
			}
		}
		
		return null;
	}
	
	
	public boolean resetForRestart(IRootSearch searcher) {
		SearcherInfo searcherinfo = searchersInfo.get(searcher);
		if (searcherinfo != null) {
			ISearchInfo lastinfo = searcherinfo.getLastSearchInfo(searcherinfo.getMaxDepth());
			if (lastinfo != null) {
				boolean isShallow = hasDepthInfo(lastinfo.getDepth() + 1);
				if (isShallow) {
					searchersInfo.remove(searcher);
					return true;
				}
			}
		}
		return false;
	}
	
	
	//Result can be null
	private ISearchInfo getInfoToSend(int depth) {
		
		
		long totalNodes = 0;
		Map<Integer, MoveInfo> movesInfoPerDepth = new HashMap<Integer, MoveInfo>();
		
		
		for (IRootSearch cur_searcher: searchersInfo.keySet()) {
			
			SearcherInfo cur_searcher_infos = searchersInfo.get(cur_searcher);
			ISearchInfo cur_last_info = cur_searcher_infos.getLastSearchInfo(depth);
			totalNodes += cur_searcher_infos.getSearchedNodes();
			
			if (cur_last_info != null) {
				MoveInfo moveInfo = movesInfoPerDepth.get(cur_last_info.getBestMove());
				if (moveInfo == null) {
					movesInfoPerDepth.put(cur_last_info.getBestMove(), new MoveInfo(cur_last_info));
				} else {
					moveInfo.addInfo(cur_last_info);
				}
			}
		}
		
		
		MoveInfo bestMoveInfo = null;
		for (Integer move: movesInfoPerDepth.keySet()) {
			MoveInfo cur_moveInfo = movesInfoPerDepth.get(move);
			if (bestMoveInfo == null) {
				bestMoveInfo = cur_moveInfo;
			} else {
				if (cur_moveInfo.getEval() > bestMoveInfo.getEval()) {
					bestMoveInfo = cur_moveInfo;
				}
			}
		}
		
		if (bestMoveInfo == null) {
			return null;
		}
		
		ISearchInfo info_to_send = SearchInfoFactory.getFactory().createSearchInfo();
		info_to_send.setDepth(bestMoveInfo.best_info.getDepth());
		info_to_send.setSelDepth(bestMoveInfo.best_info.getSelDepth());
		info_to_send.setEval(bestMoveInfo.getEval());
		info_to_send.setBestMove(bestMoveInfo.best_info.getBestMove());
		info_to_send.setPV(bestMoveInfo.best_info.getPV());
		info_to_send.setSearchedNodes(totalNodes);
		
		//if (DEBUGSearch.DEBUG_MODE) ChannelManager.getChannel().dump("SearchersInfo: getInfoToSend=" + info_to_send + (info_to_send == null ? "" : ", depth=" + info_to_send.getDepth()));
		
		return info_to_send;
	}
	
	
	public boolean hasDepthInfo(int depth) {
		
		ISearchInfo prevDepthInfo = getInfoToSend(depth - 1);
		
		int countResponded = 0;
		for (IRootSearch cur_searcher: searchersInfo.keySet()) {
			
			SearcherInfo cur_searcher_infos = searchersInfo.get(cur_searcher);
			ISearchInfo depth_last_info = cur_searcher_infos.getLastSearchInfo(depth);
			
			if (depth_last_info != null) {
				
				countResponded++;
				
				if (prevDepthInfo == null) {
					return true;
				}
				
				if (depth_last_info.getBestMove() == prevDepthInfo.getBestMove()) {
					return true;
				}
			}
		}
		
		return countResponded > 0 && countResponded == searchersInfo.size();
	}
	
	
	private static class SearcherInfo {
		
		
		private Map<Integer, SearcherDepthInfo> depthsInfo;
		
		
		public SearcherInfo() {
			depthsInfo = new HashMap<Integer, SearchersInfo.SearcherInfo.SearcherDepthInfo>();
		}
		
		
		public long getSearchedNodes() {
			
			ISearchInfo last_info = getLastSearchInfo(getMaxDepth());
			
			if (last_info == null) {
				return 0;
			}
			
			return last_info.getSearchedNodes();
		}


		public void update(ISearchInfo info) {
			SearcherDepthInfo searcherDepthInfo = depthsInfo.get(info.getDepth());
			if (searcherDepthInfo == null) {
				searcherDepthInfo = new SearcherDepthInfo();
				depthsInfo.put(info.getDepth(), searcherDepthInfo);
			}
			
			searcherDepthInfo.update(info);
		}
		
		
		public ISearchInfo getLastSearchInfo(int depth) {
			SearcherDepthInfo searcherDepthInfo = depthsInfo.get(depth);
			if (searcherDepthInfo == null) {
				return null;
			}
			return searcherDepthInfo.getLastSearchInfo();
		}
		
		
		public int getMaxDepth() {
			
			int max_depth = 0;
			for (Integer depth: depthsInfo.keySet()) {
				if (depth > max_depth) {
					max_depth = depth;
				}
			}
			
			return max_depth;
		}
		
		
		private static class SearcherDepthInfo {
			
			
			private List<ISearchInfo> infos;
			
			
			public SearcherDepthInfo() { 
				infos = new ArrayList<ISearchInfo>();
			}
			
			
			void update(ISearchInfo info) {
				infos.add(info);
			}
			
			
			public ISearchInfo getLastSearchInfo() {
				int last_index = infos.size() - 1;
				if (last_index < 0) {
					return null;
				}
				return infos.get(last_index);
			}
		}
	}
	
	
	private class MoveInfo {
		
		int sum;
		int cnt;
		int best_eval;
		ISearchInfo best_info;
		
		MoveInfo(ISearchInfo first_info) {
			
			sum = first_info.getEval();
			cnt = 1;
			best_eval = first_info.getEval();
			best_info = first_info;
			
			if (best_info == null) {
				throw new IllegalStateException("best_info == null");
			}
		}
		
		void addInfo(ISearchInfo info) {
			
			sum += info.getEval();
			cnt += 1;
			if (info.getEval() > best_eval) {
				best_eval = info.getEval();
				best_info = info;
			}
			
			if (best_info == null) {
				throw new IllegalStateException("best_info == null");
			}
		}
		
		int getEval() {
			if (SearchUtils.isMateVal(best_eval)) {
				return best_eval;
			}
			return sum / cnt;
		}
	}
}
