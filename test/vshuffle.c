/* #include "fiona_instr.h" */
#include <stdio.h>
#include <stdlib.h>
#include "rocc.h"
#include "fiona.h"
#pragma GCC optimize ("no-inline")


uint16_t src1[32] __attribute__ ((aligned (64))) = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 ,20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 };
uint16_t src2[32] __attribute__ ((aligned (64)));
uint16_t dst[32] __attribute__ ((aligned (64)));
uint16_t dst_expected[32] __attribute__ ((aligned (64)));

int main(int argc, char** argv) {
    printf("This is VSHUFFLE test. Compiled at %s\n", __TIME__);

    VLD(1, src1);
    for(int i = 0; i < 32; i++) {
        int num = rand() % 32;
        src2[i] = num;
        dst_expected[i] = src1[num];
    }
    printf("Expected result ====> \n");
    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst_expected[i]);
    }
    printf("\n");

    VLD(2, src2);
    printf("VLD: src1 -> v1; src2 -> v2.\n");

    VSHUFFLE(3, 1, 2);
    printf("VSHUFFLE: v1[v2] -> v3\n");

    VST(3, dst);
    printf("VST: v3 -> dst\n");
    printf("dst = v1[v2] ====> \n");
    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");

    printf("Finished.\n");
    return 0;
}
