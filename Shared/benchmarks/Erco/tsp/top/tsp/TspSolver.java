package tsp.top.tsp;

import java.util.BitSet;

import top.Task;
import static top.Permissions.perm;

public class TspSolver {
	
	final Config config;
	
	int curDist;
	int pathLen;
	int path[];
	BitSet visited;

	public TspSolver(Config config) {
		this.config = config;
	}

	public void topTask_run(Task now) {
		
		perm.checkRead(this);
		TourElement curr = config.getTour();
	
		perm.checkRead(curr);
		perm.checkRead(config);
		if (curr.length < (config.numNodes - config.nodesFromEnd - 1))
			/* Solve in parallel. */
			/*
			 * Create a tour and add it to the priority Q for each possible path
			 * that can be derived from the current path by adding a single node
			 * while staying under the current minimum tour length.
			 */
			//split tour
			for (int i = 0; i < config.numNodes; i++) {
				/*
				 * Check: 1. Not already in tour 2. Edge from last entry to
				 * node in graph and 3. Weight of new partial tour is less
				 * than cur min.
				 */
				perm.checkRead(config.weights);
				final int wt = config.weights[curr.node][i];
				boolean t1 = !curr.visited(i); 
				boolean t2 = (wt != 0);
				boolean t3 = (curr.lowerBound + wt) <= config.minTourLength;
				if (t1 && t2 && t3) {					
					final int newNode = i;
					TourElement newTour = new TourElement(newNode);
					newTour.previous = curr;
					newTour.length += curr.length;
					newTour.visited |= curr.visited;
					newTour.prefixWeight = curr.prefixWeight + wt;
					newTour.lowerBound = calcBound(newTour);
					
					config.enqueue(newTour);
					
					Task solveTask = new Task();
					TspSolver solver = new TspSolver(config);
					solver.topTask_run(solveTask);			
					perm.replaceNowWithTask(perm.newObject(solver), solveTask);
					perm.addTask(config, solveTask);
				}
			}
		else
			solveTour(curr); /* Solve sequentially */
		
	}

	private int calcBound(TourElement newTour) {
		assert newTour.length < config.numNodes - 2;

		/*
		 * Add up the lowest weights for edges connected to vertices not yet
		 * used or at the ends of the current tour, and divide by two. This
		 * could be tweaked quite a bit. For example: (1) Check to make sure
		 * that using an edge would not make it impossible for a vertex to
		 * have degree two. (2) Check to make sure that the edge doesn't
		 * give some vertex degree 3.
		 */

		perm.checkRead(this);
		perm.checkRead(config);
		perm.checkRead(config.weights);
		perm.checkRead(newTour);
		int mstWeight = 0;
		for (int i = 0; i < config.numNodes; i++) {
			if(newTour.visited(i)) continue;
			
			/*
			 * wt1: the value of the edge with the lowest weight from the node
			 * we're testing to another unconnected node. wt2: the value of the
			 * edge with the second lowest weight
			 */
			int wt1 = Integer.MAX_VALUE, wt2 = Integer.MAX_VALUE;
			for (int j = 0; j < config.numNodes; j++) {
				/*
				 * Ignore j's that are not connected to i
				 * (global->weights[i][j]==0),
				 */
				/* or that are already in the tour and aren't either the */
				/* first or last node in the current tour. */
				int wt = config.weights[i][j];
				if(wt == 0) continue;				

				/* Might want to check that edges go to unused vertices */
				if (wt < wt1) {
					wt2 = wt1;
					wt1 = wt;
				} else if (wt < wt2)
					wt2 = wt;
			}

			/* At least three unconnected nodes? */
			if (wt2 != Integer.MAX_VALUE)
				mstWeight += ((wt1 + wt2) >> 1);
			
			/* Exactly two unconnected nodes? */
			else if (wt1 != Integer.MAX_VALUE)
				mstWeight += wt1;
		}
		mstWeight += 1;
		return mstWeight + newTour.prefixWeight;
	}

	private void solveTour(TourElement curr) {
		perm.checkRead(curr);
		perm.checkRead(config);
		perm.checkWrite(this);
		
		curDist = curr.prefixWeight;
		pathLen = curr.length;
		visited = perm.newObject(new BitSet(config.numNodes));
		path = perm.newObject(new int[config.numNodes+1]);
		
//		StringBuilder sb = new StringBuilder("SolveTour:");
		
		perm.checkWrite(path);
		TourElement p = curr;
		for (int i = pathLen - 1; i >= 0; i--) {
//			sb.append(String.format(" %d", p.node));
			perm.checkRead(p);
			perm.checkWrite(visited);
			path[i] = p.node;
			visited.set(p.node);
			p = p.previous;			
		}
		
//		System.out.println(sb);

		visitNodes(path[pathLen - 1]);
	}


	/*
	 * visit_nodes()
	 * 
	 * Exhaustively visits each node to find Hamilton cycle. Assumes that search
	 * started at node from.
	 */	
	void visitNodes(int from) {
		int i;
		int dist, last;

		perm.checkWrite(this);
		perm.checkWrite(visited);
		perm.checkRead(config);
		perm.checkRead(config.weights);
		perm.checkWrite(path);
		
		for (i = 1; i < config.numNodes; i++) {
			if (visited.get(i))
				continue; /* Already visited. */
			if ((dist = config.weights[from][i]) == 0)
				continue; /* Not connected. */
			if (curDist + dist > config.minTourLength) {
//				if(path[1] == 2 && path[2] == 8 && path[3] == 7 && path[4] == 6 && path[5] == 4 && path[6] == 5)
//					System.err.printf("  (Abandoned %s with bound %d)\n", Arrays.toString(path), curDist + dist);
				continue; /* Path too long. */
			}

			/* Try next node. */
			visited.set(i);
			path[pathLen++] = i;
			curDist += dist;

			if (pathLen == config.numNodes) {
				/* Visiting last node - determine if path is min length. */
				if ((last = config.weights[i][config.startNode]) != 0
						&& (curDist += last) < config.minTourLength) 
				{
					path[pathLen] = config.startNode;
					config.setBest(curDist, path);
				}
				curDist -= last;
			} /* if visiting last node */
			else if (curDist < config.minTourLength)
				visitNodes(i); /* Visit on. */

			/* Remove current try and loop again at this level. */
			curDist -= dist;
			path[--pathLen] = 0;
			//pathLen--;
			visited.clear(i);
		}
	}
	
}
