/* #include "fiona_instr.h" */
#include <stdio.h>
#include "rocc.h"
#include "fiona.h"
#pragma GCC optimize ("no-inline")


uint16_t src1[32] __attribute__ ((aligned (64))) = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 ,20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 };
uint16_t src2[32] __attribute__ ((aligned (64))) = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 ,21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32 };

uint16_t dst[32] __attribute__ ((aligned (64)));

int main(int argc, char** argv) {
    printf("This is VSADD test. Compiled at %s\n", __TIME__);

    VLD(1, src1);
    VLD(2, src2);
    printf("VLD: src1 -> v1; src2 -> v2.\n");
    printf(" ====> Setting VL = 9\n");
    int vl = 9;
    SETVL(vl);
    VADD(3, 1, 2);
    printf("VADD: v1 + v2 -> v3\n");
    VST(3, dst);
    printf("VST: v3 -> dst\n");
    printf("dst = v1 + v2 ====> \n");
    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");

    VSUB(4, 2, 1);
    printf("VSUB: v2 - v1 -> v4\n");

    VST(4, dst);
    printf("VST: v4 -> dst\n");
    printf("dst = v2 - v1 ====> \n");

    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");

    printf(" ====> Setting VL = 32\n");
    vl = 32;
    SETVL(vl);

    VLD(1, src1);
    VLD(2, src2);
    printf("VLD: src1 -> v1; src2 -> v2.\n");

    VADD(3, 1, 2);
    printf("VADD: v1 + v2 -> v3\n");

    VST(3, dst);
    printf("VST: v3 -> dst\n");
    printf("dst = v1 + v2 ====> \n");
    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");

    VSUB(4, 2, 1);
    printf("VSUB: v2 - v1 -> v4\n");

    VST(4, dst);
    printf("VST: v4 -> dst\n");
    printf("dst = v2 - v1 ====> \n");

    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");
    
    

    printf("Finished.\n");
    return 0;
}
