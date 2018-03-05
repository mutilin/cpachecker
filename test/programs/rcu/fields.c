#include "rcu.h"

struct foo {
  void * gp;
} * pStruct;

int main() {
  pStruct = calloc(1, sizeof(struct foo));
  pStruct -> gp = calloc(2, sizeof(int));
  int * mem = calloc(3, sizeof(int));

  mem[0] = 'r';
  mem[1] = 'c';
  mem[2] = 'u';

  int * ptr = pStruct -> gp;

  ldv_wlock_rcu();
  ldv_rcu_assign_pointer(pStruct -> gp, mem);
  ldv_wunlock_rcu();

  ldv_synchronize_rcu();

  ldv_free(ptr);

  return 0;
}
