s = Server.default;

s.options.inDevice_("Steinberg UR-RT2");
s.options.outDevice_("Steinberg UR-RT2");

s.waitForBoot {
	Routine {

		SynthDef.new(\patch2, {arg in, out, amp=1; Out.ar(out, In.ar(in, 2)*amp)}).send(s);
		SynthDef.new(\adc2, {arg in, out, amp=1; Out.ar(out, SoundIn.ar([in, in+1])*amp)}).send(s);

		// master audio context
		~main_in_b = Bus.audio(s, 2);
		~main_out_b = Bus.audio(s, 2);

		~ig = Group.new;
		~xg = Group.after(~ig);
		~og = Group.after(~xg);

		s.sync;

		~hw_in_s = Synth.new(\adc2, [\in, 0, \out, ~main_in_b.index], ~ig, \addBefore);
		~hw_out_s = Synth.new(\patch2, [\in, ~main_out_b.index, \out, 0], ~og, \addAfter);

		ZEcho.addSynthDefs(s);
		s.sync;
		z = ZEcho.new(s, ~xg, [
			\dubLevel, -4.dbamp,
			\lpfFc, 60.midicps*2, \hpfFc, 36.midicps,
			\hpfRq, 0.2, \hpfRq, 0.2,
			\time, 2.5]);

		~z_in_s = Synth.new(\patch2, [\in, ~main_in_b.index, \out, z.bus[\in].index], ~ig);
		~z_out_s = Synth.new(\patch2, [\in, z.bus[\out].index, \out, ~main_out_b.index], ~og);

		{ z.bus[\out].scope }.defer;

	}.play;
}