/* #include "fiona_instr.h" */
#include <stdio.h>
#include "rocc.h"
#include "fiona.h"
#pragma GCC optimize ("no-inline")


uint16_t src1[32] __attribute__ ((aligned (64))) = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 ,20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 };

uint16_t dst[32] __attribute__ ((aligned (64)));

int main(int argc, char** argv) {
    printf("This is VSMUL/DIV test. Compiled at %s\n", __TIME__);

    VLD(1, src1);
    printf("VLD: src1 -> v1.\n");

    uint16_t src2 = 10;

    VSMUL(2, src2, 1);
    printf("VSMUL: v1 * 10 -> v2\n");

    VST(2, dst);
    printf("VST: v2 -> dst\n");

    printf("dst = v1 * 10 ====> \n");
    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");

    VLD(3, dst);
    printf("VLD: dst -> v3.\n");

    src2 = 5;
    VSDIV(4, src2, 3);
    printf("VSDIV: v3 / 5 -> v4\n");

    VST(4, src1);
    printf("VST: v4 -> src1\n");

    printf("src1 = v1 * 10 / 5 ====> \n");
    for(int i = 0; i < 32;i ++) {
        printf("%d ", src1[i]);
    }
    printf("\n");

    printf("Setting vlen to 6\n");
    int vl = 6;
    SETVL(vl);


    VLD(1, src1);
    printf("VLD: src1 -> v1.\n");

    src2 = 100;

    VSMUL(2, src2, 1);
    printf("VSMUL: v1[:vl] * 100 -> v2[:vl]\n");

    VST(2, dst);
    printf("VST: v2[:vl] -> dst[:vl]\n");

    printf("dst = v1[:vl] * 100 ====> \n");
    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");

    printf("Setting vlen to 8\n");
    vl = 8;
    SETVL(vl);

    VLD(3, dst);
    printf("VLD: dst[:vl] -> v3.\n");

    src2 = 10;
    VSDIV(4, src2, 3);
    printf("VSDIV: v3[:vl] / 10 -> v4\n");

    VST(4, dst);
    printf("VST: v4[:vl] -> dst\n");

    for(int i = 0; i < 32;i ++) {
        printf("%d ", dst[i]);
    }
    printf("\n");


    printf("Finished.\n");
    return 0;
}
