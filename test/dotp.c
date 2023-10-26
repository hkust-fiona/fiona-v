/* #include "fiona_instr.h" */
#include <stdio.h>
#include "rocc.h"
#include "fiona.h"
#pragma GCC optimize ("no-inline")


uint16_t src1[32] __attribute__ ((aligned (64))) = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31};
uint16_t src2[32] __attribute__ ((aligned (64))) = {32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1};


uint16_t dst;

int main(int argc, char** argv) {
    printf("This is DOTP test. Compiled at %s\n", __TIME__);

    VLD(1, src1);
    printf("VLD: src1 -> v1\n");

    VLD(2, src2);
    printf("VLD: src2 -> v2\n");

    DOTP(dst, 1, 2);
    printf("DOTP: v1 v2 -> dst = %d\n", dst);

    printf("Setting VL to 16\n");

    int vl = 16;
    SETVL(vl);
    DOTP(dst, 1, 2);
    printf("DOTP: v1 v2 -> dst = %d\n", dst);

    printf("\n");
    printf("Finished.\n");
    return 0;
}
