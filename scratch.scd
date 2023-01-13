//z.buf

r = Routine {
	var lpfFc, hpfFc, lpfRq, hpfRq;

	inf.do {
		lpfFc =  48.midicps * [2, 3, 4, 5, 6].choose / [1, 2].choose;
		hpfFc = lpfFc / [2, 3, 4].choose;
		lpfRq = 0.6.rand + 0.2;
		hpfRq = 0.6.rand + 0.2;
		[lpfFc, hpfFc, lpfRq].postln;
		z.setSynthParam(\lpfFc, lpfFc);
		z.setSynthParam(\hpfFc, hpfFc);
		z.setSynthParam(\lpfRq, lpfRq);
		z.setSynthParam(\hpfRq, hpfRq);
		z.setDelayTime([1, 2, 3].choose);
		[2, 3, 4].choose.wait;
} } .play;

r.stop;




z.setSynthParam(\phaseSlewRate, 6/5 - 1);
z.setSynthParam(\phaseLagTime, 2)

z.setSynthParam(\dubLevel, -6.dbamp);

z.setSynthParam(\outLevel, -12.dbamp);