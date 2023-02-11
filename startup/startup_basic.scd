s = Server.default;
s.waitForBoot {
	Routine {
		c = ZAudioContext.new;
	}.play;
}