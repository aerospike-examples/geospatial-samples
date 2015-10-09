
ifndef CLIENTREPO
  CLIENTREPO = $(HOME)/aerospike/aerospike-client-c
  $(warning CLIENTREPO env variable not set; using $(CLIENTREPO))
endif

AEROSPIKE_CLIENT_ROOT = $(CLIENTREPO)

OS = $(shell uname)

AS_PLATFORM_DIR=Linux-x86_64

ifeq ($(OS),Darwin)
AS_PLATFORM_DIR=Darwin-x86_64
endif

CFLAGS +=	-g -Wall

LIBS += 	\
			-lpthread \
			-ljansson \
			-lcrypto \
			$(NULL)

ASINCS +=	-I$(AEROSPIKE_CLIENT_ROOT)/target/$(AS_PLATFORM_DIR)/include


ASLIBS +=		\
			-Wl,-rpath,$(AEROSPIKE_CLIENT_ROOT)/target/$(AS_PLATFORM_DIR)/lib \
			-L$(AEROSPIKE_CLIENT_ROOT)/target/$(AS_PLATFORM_DIR)/lib \
			-laerospike \
			-llua \
			-ldl \
			-lcrypto \
			$(NULL)

ifeq ($(OS),Darwin)
else
ASLIBS += \
			-lm \
			-lrt \
			$(NULL)
endif



INCS += $(ASINCS)
LIBS += $(ASLIBS)

OBJDIR = OBJS

PROG1 =	osm_around

SRCS1 = osm_around.cpp

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
