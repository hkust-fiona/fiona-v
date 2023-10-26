/* #include "fiona_instr.h" */
#include <stdio.h>
#include "rocc.h"
#include "fiona.h"
#pragma GCC optimize ("no-inline")


uint16_t vec[8] __attribute__ ((aligned (64))) = {32,31,30,29,28,27,26,25};
uint16_t mat [64] __attribute__ ((aligned (64))) = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63};

uint16_t dst[64] __attribute__ ((aligned (64)));

int main(int argc, char** argv) {
    printf("This is MVM test. Compiled at %s\n", __TIME__);
    
    int vl = 8;
    SETVL(vl);

    VLD(1, vec);
    printf("VLD: vec -> v1\n");

    SETMAT(mat);
    printf("Loading matrix reg\n");

    MVM(2, 1);
    printf("MVM -> dst\n");
    VST(2, dst);

    for(int i = 0; i < vl; i++) {
      printf("%d\n", dst[i]);
    }

    printf("\n");
    printf("Finished.\n");
    return 0;
}
