// function generator based on interpolating output from iterative map
/// abstract base class
ZMapLfo {

	var <history;
	// interpolation mode as an integer:
	// 0 = no interpolation
	// 1 = linear
	// 2 = cubic
	// 3 = sine segment
	var <>interpolationMode;
	var <>phase;
	var <>increment;
	var lastOutput=0;

	*new { ^super.new.init }

	init {
		history = LinkedList.new;
		4.do({ arg i; history.add(0) });
		phase = 0;
		increment = 0.1;
	}

	tick {
		phase = phase + increment;
		if (phase > 1, {
			phase = phase % 1;
			this.update;
		});
		// history.postln;
		lastOutput = ZMapLfo.interpolateHistory(phase, history, interpolationMode);
		^lastOutput
	}

	update {
		var newValue = this.getNextValue;
		// newValue.postln;
		history.popFirst;
		history.add(newValue);
	}


	*interpolateHistory {arg x, history, mode;
		^switch(mode,
			// 0: none (S+H)
			{0}, { history.last },
			// 1: linear
			{1}, { history[2].blend(history[3], x) },
			// 2: cubic hermite
			{2}, { ZMapLfo.interpolateCubic(x, history[0], history[1], history[2], history[3]) },
			// 3: cosine sections
			{3}, { history[2].blend(history[3], cos(pi * (x+1)) * 0.5 + 0.5) },
			// 4: circular sections (after Penner)
			/*
			 x < 0.5
  ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) / 2
  : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) / 2;
			*/
			{4}, { history[2].blend(history[3],
				if(x < 0.5, { 
					var tmp = 2*x;
					tmp = tmp * tmp
					(1 - (1-tmp).sqrt) * 0.5
				}, {
					var tmp = -2 * x + 2;
					tmp = tmp * tmp;
					((1 - tmp).sqrt + 1) * 0.5
				})
			)}
		)

	}

	*interpolateCubic { arg x, y0, y1, y2, y3;
		var c0 = y1;
		var c1 = 0.5 * (y2 - y0);
		var c2 = y0 - (2.5 * y1) + (2 * y2) - (0.5 * y3);
		var c3 = (0.5 * (y3 - y0)) + (1.5 * (y1 - y2));
		^(((c3 * x) + c2) * x + c1) * x + c0;
	}

	// subclasses should override
	getNextValue { ^0 }
}

ZMapLfo_User : ZMapLfo {
	var <>state;
	var <>nextValueFunc;
	var <>returnValueFunc;

	getNextValue {
		nextValueFunc.value(state);
		^returnValueFunc.value(state);
	}
}

ZMapLfo_Cubic : ZMapLfo {
	var <>a = 3.77;
	var <>x = 0;

	getNextValue {
		x = (a*x*x*x) + ((1-a)*x);
		^x
	}
}