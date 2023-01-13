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

			var input, output;
			input = In.ar(in, 2) * inLevel;


			output = input * outLevel;
			Out.ar(out, output);

		}).send(aServer);
	}

	init {
		arg aServer, aTargetNode;
		bus = Dictionary.new;
		bus[\in] = Bus.audio(aServer, 2);
		bus[\out] = Bus.audio(aServer, 2);
		buf = Buffer.alloc(aServer, aServer.sampleRate * maxDelayTime, 2);
		synth = Synth.new(\ZEcho, [\in, bus[\in].index, \out, bus[\out].index, \buf, buf.bufnum], aTargetNode);
	}

	setDelayTime { arg time;
		setSynthParam(\time, time);
	}

	setSynthParam { arg key, value;
		synth.set(key, value);
	}

}