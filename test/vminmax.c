/* #include "fiona_instr.h" */
#include <stdio.h>
#include "rocc.h"
#include "fiona.h"
#pragma GCC optimize ("no-inline")


uint16_t src[32] __attribute__ ((aligned (64))) = {13,15,6,9,4,20,24,24,8,7,31,31,11,17,28,25,26,22,9,12,5,23,20,4,4,15,21,8,4,9,23,27};

uint16_t dst_min;
uint16_t dst_max;

int main(int argc, char** argv) {
    printf("This is VMIN/VMAX test. Compiled at %s\n", __TIME__);

    VLD(1, src);
    printf("VLD: src -> v1\n");

    VMIN(dst_min, 1);
    printf("VMIN: v1 -> dst_min = %d\n", dst_min);

    VMAX(dst_max, 1);
    printf("VMAX: v1 -> dst_max = %d\n", dst_max);

    int vl = 5;
    SETVL(vl);
    printf("=====Setting VL=5\n");

    VMIN(dst_min, 1);
    printf("VMIN: v1 -> dst_min = %d\n", dst_min);

    VMAX(dst_max, 1);
    printf("VMAX: v1 -> dst_max = %d\n", dst_max);

    printf("\n");
    printf("Finished.\n");
    return 0;
}
