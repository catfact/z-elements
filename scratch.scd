//z.buf

r = Routine { inf.do {
	z.setDelayTime(4.0);
	5.wait;
	z.setDelayTime(0.2);
	5.wait;
	z.setDelayTime(3);
	5.wait;
	z.setDelayTime(0.5);
	5.wait;
} } .play;
