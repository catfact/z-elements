// base class for echo
ZEcho {

	var <server;
	var <synth;
	var <bus;
	var <buf;

	classvar maxDelayTime;

	*new {
		arg server, targetNode, synthArgs;
		^super.new.init(server, targetNode, synthArgs);
	}

	*addSynthDefs {
		arg aServer, aMaxDelayTime=8;
		maxDelayTime = aMaxDelayTime;

		SynthDef.new(\ZEcho, {
			arg buf, in, out,
			inLevel=1, outLevel=1,
			dubLevel=0,
			time=1, phaseLagTime=0.1, phaseSlewRate=2;

			var input, output, phaseWrite, phaseRead, bufFrames, phaseOffset;

			input = In.ar(in, 2) * inLevel;
			input = input + (LocalIn.ar(2) * dubLevel);

			bufFrames = BufFrames.ir(buf);
			phaseWrite = Phasor.ar(rate:1, start:0, end:bufFrames);

			phaseOffset = K2A.ar(time * SampleRate.ir);
			phaseSlewRate = phaseSlewRate*SampleRate.ir;
			phaseOffset = Slew.ar(phaseOffset, phaseSlewRate, phaseSlewRate);
			phaseOffset = Lag.ar(phaseOffset, phaseLagTime);
			phaseRead = phaseWrite - phaseOffset + bufFrames;

			BufWr.ar(input, buf, phaseWrite);
			output = BufRd.ar(2, buf, phaseRead);

			LocalOut.ar(output);


			Out.ar(out, output);

		}).send(aServer);
	}

	// NB: must be run inside a Routine/Task/Thread!!
	init {
		arg aServer, aTargetNode, aSynthArgs;
		var synthArgs;

		server = aServer;

		bus = Dictionary.new;
		bus[\in] = Bus.audio(aServer, 2);
		bus[\out] = Bus.audio(aServer, 2);

		buf = Buffer.alloc(server, server.sampleRate * maxDelayTime, 2);

		server.sync;

		synthArgs = [\in, bus[\in].index, \out, bus[\out].index, \buf, buf.bufnum];
		if (aSynthArgs.notNil, { synthArgs = synthArgs ++ aSynthArgs });
		synth = Synth.new(\ZEcho, synthArgs, aTargetNode);
	}

	setDelayTime { arg time;
		this.setSynthParam(\time, time);
	}

	setSynthParam { arg key, value;
		synth.set(key, value);
	}

}