s = Server.default;
s.options.inDevice_("Universal Audio Thunderbolt");
s.options.outDevice_("Universal Audio Thunderbolt");

s.waitForBoot {
	Routine {
		c = ZAudioContext.new;

		m = ZMidiInput.new;

		// ~test_sine = { Out.ar(c.bus[\hw_out], SinOsc.ar(110).dup * 0.1) }.play(c.group[\process]);
		/// ~test_sine.free;

		ZEcho.addAllSynthDefs;

		z = ZEchoSlewMod.new(c.server, c.group[\process]);
		~z_in = Synth.new(\patch_stereo, [\in, c.bus[\hw_in], \out, z.bus[\in]], c.group[\in]);
		~z_out = Synth.new(\patch_stereo, [\out, c.bus[\hw_out], \in, z.bus[\out]], c.group[\out]);

		~monitor = Synth.new(\patch_stereo, [\out, c.bus[\hw_out], \in, c.bus[\hw_in], \level, -9.dbamp], c.group[\out]);

		{ z.bus[\in].scope; }.defer;

		t = SystemClock.seconds;

		~interval_tracker = ZIntervalTracker.new;

		m.setHandler(\noteOn, { arg args;
			var vel = args[0];
			var num = args[1];
			postln("noteon: " ++ vel ++ ", " ++ num);
			if (num == 40, {
				var t0 = t;
				postln("hi");
				t = SystemClock.seconds;
				d = t - t0;
				postln("setting delay time: " ++ d);
				z.setDelayTime(d);
			}, {
				var interval = ~interval_tracker.noteOn(num);
				postln(interval);
				if (interval.notNil, {
					var ratio = interval.midiratio;
					postln(ratio);
					z.setSynthParam(\slewRateLimit, ratio);
				});
			});
		});

		m.setHandler(\noteOff, { arg args;
			var vel = args[0];
			var num = args[1];
			var interval = ~interval_tracker.noteOff(num);
			postln(interval);
		});

		m.setHandler(\cc, { arg args;
			var val = args[0];
			var num = args[1];
			if (num == 70, {
				var level = if(val == 0, { 0}, { val.linlin(1, 127, -40, -6).dbamp });
				postln("setting feedback level: " ++ level);
				z.setFeedbackLevel(level);
			});

			if (num == 71, {
				var time = 0.125 * (2 ** (1 + val.linlin(0, 127, 0, 5)));
				postln("setting delay time: " ++ time);
				z.setDelayTime(time);
			});
		});


		z.setSynthParam(\slewRateLimitUp, 3);
		z.setSynthParam(\slewRateLimitDown, 2);

	}.play;
}