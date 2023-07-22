ZTsingouRing {
	var <>n; 		// number of weights
	var <>a; 		// nonlinearity parameter
	var <>dt; 		// time step of simulation
	var <>k;		// force constant
	var <>d;		// damping coefficient
	var <>wrap; 	// wrap positions?
	var <>clip; 	// clip positions?
	var <>val, <>vel, <>acc; // current position, velocity, acceleration for all weights

	*new { arg n;
		^super.newCopyArgs(n).init;
	}

	init {
		a = 2.4;
		dt = 0.02;
		k = 1.0;
		d = 0.975;
		clip = false;
		wrap = true;
		val = Array.fill(n, {0.0});
		vel = Array.fill(n, {0.0});
		acc = Array.fill(n, {0.0});
	}

	iterate {
		n.do({
			arg i;
			var dist;

			// alternately:     F(x) = -k x + b x3
			// displacement distance:
			dist = val[(i+1).wrap(0, n-1)] - val[i];
			dist = dist + val[(i-1).wrap(0, n-1)] - val[i];

			// [i, dist, val[i], acc[i], vel[i]].postln;

			acc[i] = -1* k * dist + (a * dist * dist * dist);

			// update velocity from acceleration
			vel[i] = vel[i] + (acc[i] * dt);
			// damping
			vel[i] = vel[i] * d;

			// update position from velocity
			val[i] = val[i] + (vel[i] * dt);
			if (wrap, {val[i] = val[i].wrap(-1.0, 1.0); });
			if (clip, {val[i] = val[i].clip(-1.0, 1.0); });
		});
	}
}