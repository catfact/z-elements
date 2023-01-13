// base class for echo
ZEcho {

	var <server;
	var <synth;
	var <bus;
	var <buf;

	classvar maxDelayTime;

	*new {
		arg server, targetNode;
		^super.new.init(server, targetNode);
	}

	*addSynthDefs {
		arg aServer, aMaxDelayTime=8;
		maxDelayTime = aMaxDelayTime;

		SynthDef.new(\ZEcho, {
			arg buf, in, out,
			inLevel=1, outLevel=1,
			time=1;

			var input, output, phaseWrite, phaseRead, bufFrames, phaseOffset;
			input = In.ar(in, 2) * inLevel;


			// this dont work cause we want stereo!~!@!
			//output = BufDelayN.ar(buf, input, time) * outLevel;

			bufFrames = BufFrames.ir(buf);
			phaseRead = Phasor.ar(rate:1, start:0, end:bufFrames);
			phaseOffset = time * SampleRate.ir;
			phaseWrite = phaseRead + phaseOffset;

			BufWr.ar(input, buf, phaseWrite);
			output = BufRd.ar(2, buf, phaseRead);


			Out.ar(out, output);

		}).send(aServer);
	}

	// NB: must be run inside a Routine/Task/Thread!!
	init {
		arg aServer, aTargetNode;
		server = aServer;

		bus = Dictionary.new;
		bus[\in] = Bus.audio(aServer, 2);
		bus[\out] = Bus.audio(aServer, 2);

		buf = Buffer.alloc(server, server.sampleRate * maxDelayTime, 2);

		server.sync;
		synth = Synth.new(\ZEcho, [\in, bus[\in].index, \out, bus[\out].index, \buf, buf.bufnum], aTargetNode);
	}

	setDelayTime { arg time;
		setSynthParam(\time, time);
	}

	setSynthParam { arg key, value;
		synth.set(key, value);
	}

}