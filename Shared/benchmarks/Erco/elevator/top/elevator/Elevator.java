package elevator.top.elevator;
/*
 * Copyright (C) 2000 by ETHZ/INF/CS
 * All rights reserved
 * 
 * @version $Id: Elevator.java 2094 2003-01-30 09:41:18Z praun $
 * @author Roger Karrer
 */

import java.util.*;
import java.io.*;

import top.Task;
import static top.Permissions.perm;

public class Elevator {

	// shared control object
	private static Controls controls;
	private static Vector<ButtonPress> events;
	private final int numFloors, numLifts;

	// Initializer for main class, reads the input and initlizes
	// the events Vector with ButtonPress objects
	private Elevator() {
		InputStreamReader reader = new InputStreamReader(System.in);
		StreamTokenizer st = new StreamTokenizer(reader);
		st.lowerCaseMode(true);
		st.parseNumbers();

		events = new Vector<ButtonPress>();

		int numFloors = 0, numLifts = 0;
		try {
			System.out.print("How many floors? ");
			numFloors = readNum(st);
			System.out.print("How many lifts? ");
			numLifts = readNum(st);

			int time = 0, to = 0, from = 0;
			do {
				System.out.print("At Time (0 to start simulation)? ");
				time = readNum(st);
				if (time != 0) {
					System.out.print("From floor? ");
					from = readNum(st);
					System.out.print("To floor? ");
					to = readNum(st);
					events.addElement(new ButtonPress(time, from, to));
				}
			} while (time != 0);
		} catch (IOException e) {
			System.err.println("error reading input: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// Create the shared control object
		controls = new Controls(numFloors);
		this.numFloors = numFloors;
		this.numLifts = numLifts;
	}
	
	public void topTask_doButtonPresses(Task now) {
		// First tick is 1
		int time = 1;
		
		perm.checkRead(events);
		for (int i = 0; i < events.size();) {
			ButtonPress bp = (ButtonPress) events.elementAt(i);
			// if the current tick matches the time of th next event
			// push the correct buttton
			perm.checkRead(bp);
			if (time == bp.time) {
				System.out
						.println("Elevator::begin - its time to press a button");
				if (bp.onFloor > bp.toFloor)
					controls.pushDown(bp.onFloor, bp.toFloor);
				else
					controls.pushUp(bp.onFloor, bp.toFloor);
				i += 1;
			}
			// wait 1/2 second to next tick
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
			time += 1;
		}
		controls.setTerminated();
	}

	// Press the buttons at the correct time
	public void topMainTask_begin(Task now) {
		//first fix the permissions. This should have been done in the constructor but the constructor
		//runs in the main() method outside a task. So we "simulate" this here.
		perm.newObject(events);		
		//register the button presses and link them to the events permissions
		for(ButtonPress bp : events) {
			perm.linkKeychains(events, perm.newObject(bp));
		}
		
		perm.newObject(controls);
		perm.newObject(controls.floors);
		for(Floor f : controls.floors) {
			perm.newObject(f);
			perm.makeShared(f);
			perm.linkKeychains(f, perm.newObject(f.downPeople));
			perm.linkKeychains(f, perm.newObject(f.upPeople));
		}
		
		// Create the elevators
		for (int i = 0; i < numLifts; i++) {
			Lift lift = new Lift(numFloors, controls);
						
			Task liftBegin = new Task();
			lift.topTask_begin(liftBegin);
			//lift is owned by its own task
			perm.replaceNowWithTask(lift, liftBegin);
			//controls.floors is read-shared
			perm.addTask(controls.floors, liftBegin);
		}
		
		Task buttonPress = new Task();
		this.topTask_doButtonPresses(buttonPress);
		
		//buttonPress owns events and controls
		//the controls.floors array is shared, though 
		perm.replaceNowWithTask(events, buttonPress);
		perm.replaceNowWithTask(controls, buttonPress);
		perm.addTask(controls.floors, buttonPress);
	}

	private int readNum(StreamTokenizer st) throws IOException {
		int tokenType = st.nextToken();

		if (tokenType != StreamTokenizer.TT_NUMBER)
			throw new IOException("Number expected!");
		return (int) st.nval;
	}

	public static void main(String args[]) {
		Elevator building = new Elevator();
		
		building.topMainTask_begin(new Task());
	}
}
