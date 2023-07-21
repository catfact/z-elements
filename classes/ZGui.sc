

// general purpose GUI class
ZGui {
	var <win;
	var <view;

	//// user redraw function
	var <>redraw;

	//// user callbacks:
	// fires on any mouse position change
	var <>onMousePosition;
	// fires on mouse click / drag
	var <>onMouseButton;
	// fires on mouse wheel movement
	var <>onMouseWheel;
	// fires on any non-modifier keydown
	var <>onKeyDown;
	// fires on any non-modifier keyup
	var <>onKeyUp;
	// current modifier keys as bitfield (accessor functions also exist)
	var <mods;

	*new { ^super.new.init }

	init {
		onMousePosition = {};
		onMouseButton = {};
		onMouseWheel = {};
		onKeyDown = {};
		onKeyUp = {};
		AppClock.sched(0, {
			win = Window.new;
			win.view.background = Color.black;

			view = UserView(win, win.view.bounds);

			view.onResize_({ arg v; postln("new bounds: " ++ v.bounds); });
			win.view.onResize_({arg v; view.bounds = v.bounds; });

			view.acceptsMouse_(true);
			win.acceptsMouseOver_(true);

			view.mouseOverAction_({
				arg v, x, y;
			});

			view.mouseMoveAction_({
				arg v, x, y, mods;
				this.mods = mods;
			});

			view.mouseDownAction_({

			});

			view.mouseUpAction_({

			});

			view.keyDownAction_({

			});

			view.keyUpAction_({

			});

			win.front;
		});
	}
}
