
void *kzalloc(unsigned long size);

void __VERIFIER_error(void);

void *zzzalloc(unsigned long size) {
   return kzalloc(size);
}


void *zzalloc(unsigned long size) {
	void * result = zzzalloc(size);
	unsigned long i;
	i = (unsigned long)&i + (unsigned long)result;
	if (i < 0) {
		__VERIFIER_error();
	}
	return result;
}

void *zalloc(unsigned long size) {
	void *result = zzalloc(size);
	return result;
}

struct arr { int arr[30]; };

int entry_point() {
	int i = 0;
        struct arr *arr = zalloc(30);
        struct arr *arr2 = zalloc(10); 
	if (!arr || !arr2) return 0;
	while (i < 30) {
		arr->arr[i] = i;
		i++;
	}
	
	i = 9;
        while (i >= 0) {
		arr2->arr[i] = -i;
		i--;
	}
	
	if (arr->arr[0] != 0) {
		__VERIFIER_error();
	}
	return 0;
}
