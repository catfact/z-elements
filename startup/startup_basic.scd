s = Server.default;

~elementsBasePath = "/Users/emb/code/z-elements";

s.waitForBoot {
	Routine {
		c = ZAudioContext.new(s);

		postln("server sample rate: " ++ s.sampleRate);
		c.playTestInputLoop(~elementsBasePath ++ "/resources/dont_explain_short_48k.wav");

		~fx = ZStereoAuxLoop.new(c);
		~fx.setDryLevel(-6.dbamp);

		{ c.bus[\hw_in].scope; }.defer;
	}.play;
}