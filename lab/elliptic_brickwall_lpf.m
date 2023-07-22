% script to generate brickwall lowpass filter coefficients for supercollider

pkg load signal;

% with biquad filters, all coefficients can be calculated in terms of unit frequency
% so we need not specify specific cutoff frequencies, only ratios of nyquist
ratios = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16];

order = 8;
ripple = 4;
attenuation = 60;

printf("{\n\n")

for r = ratios
    % design
    [b,a] = ellip(order, ripple, attenuation, 1/r, 'low');
    [sos, g] = tf2sos(b,a);

    % matlab only:
    % h = fvtool(b,a);

    % output
    printf("var brickwall_lpf_%d = (\n", r);
    printf("  g: %f,\n", g);
    printf("  c: [\n");
    for i = 1:rows(sos)
        printf("    [");
        for j = 1:columns(sos)
            printf("%f", sos(i,j));
            if (j < columns(sos))
                printf(", ");
            endif
        endfor
        printf("    ]");
        if (i < rows(sos))
            printf(",\n");
        endif
    end
    printf("\n  ]\n);\n\n");
end

printf("Dictionary.newFrom([\n");
for r = ratios
    printf("  %d, brickwall_lpf_%d,\n", r, r);
end
printf("])\n\n");
printf("}\n\n");