
ifndef CLIENTREPO
  CLIENTREPO = $(HOME)/aerospike/aerospike-client-c
  $(warning CLIENTREPO env variable not set; using $(CLIENTREPO))
endif

AEROSPIKE_CLIENT_ROOT = $(CLIENTREPO)

CFLAGS +=	-g -Wall

READOSMLIBS =	-lreadosm

LIBS += 	\
			$(READOSMLIBS)	\
			-lpthread \
			-ljansson \
			-lcrypto \
			$(NULL)

ASINCS +=	-I$(AEROSPIKE_CLIENT_ROOT)/target/Linux-x86_64/include

ASLIBS +=		\
			-Wl,-rpath,$(AEROSPIKE_CLIENT_ROOT)/target/Linux-x86_64/lib \
			-L$(AEROSPIKE_CLIENT_ROOT)/target/Linux-x86_64/lib \
			-laerospike \
			-llua \
			-ldl \
			-lcrypto \
			-lrt \
			$(NULL)

INCS += $(ASINCS)
LIBS += $(ASLIBS)

OBJDIR = OBJS

PROG1 =	osm_load

SRCS1 = osm_load.cpp

OBJS1.c = $(SRCS1:%.c=$(OBJDIR)/%.o)
OBJS1   = $(OBJS1.c:%.cpp=$(OBJDIR)/%.o)

BLTPROG1 = $(PROG1:%=$(OBJDIR)/%)

all: $(BLTPROG1)

$(BLTPROG1):	$(OBJS1)
	@$(CHKDIR)
	g++ $(CFLAGS) $(INCS) -o $@ $(OBJS1) $(LIBS)

$(OBJDIR)/%.o:	%.c
	@$(CHKDIR)
	gcc $(CFLAGS) $(INCS) -c -o $@ $<

$(OBJDIR)/%.o:	%.cpp
	@$(CHKDIR)
	g++ $(CPPFLAGS) $(CFLAGS) $(INCS) -c -o $@ $<

clean:
	rm -f $(BLTPROG1) $(OBJS1)

define CHKDIR
if test ! -d $(@D); then install -d $(@D); else true; fi
endef

.PRECIOUS:	$(OBJS1)
