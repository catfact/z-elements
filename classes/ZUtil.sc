ZIntervalTracker {

	var noteHeldStack;

	*new {
		^super.new.init
	}

	init {
		noteHeldStack = LinkedList.new;
	}

	getInterval {
		var count;
		count = noteHeldStack.size;
		if(count>1, {
			^(noteHeldStack[count-1] - noteHeldStack[count-2])
		}, {
			^nil
		});
	}

	noteOn { arg num;
		noteHeldStack.add(num);
		noteHeldStack.postln;
		^this.getInterval
	}

	noteOff { arg num;
		noteHeldStack.remove(num);
		noteHeldStack.postln;
		^this.getInterval
	}

}