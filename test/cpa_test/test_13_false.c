void
func0(void)
{
    return;
}

struct str0
{
    int b;
    void (*fptr)(void);
};

struct str
{
    int a;
    struct str0 *st0;
};

void
func1(struct str *st_in)
{
    struct str0 *st2;
    st2 = st_in->st0;
    if ((unsigned long)st2->fptr != (unsigned long) (void (*)(void))0)
    {
        st2->fptr();
ERROR: goto ERROR;
    }
    else
    {
    }
}

int
main(int argc, char **argv)
{
    struct str0 *st_dop;
    st_dop->fptr = func0;
    struct str *st;
    st->st0 = st_dop;
    func1(st);
    return 0;
}

